package ucar.nc2.ft.point.bufr;

import org.jdom2.Element;
import ucar.ma2.StructureData;
import ucar.ma2.StructureDataIterator;
import ucar.nc2.Attribute;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.SequenceDS;
import ucar.nc2.ft.*;
import ucar.nc2.ft.point.*;
import ucar.nc2.iosp.IOServiceProvider;
import ucar.nc2.iosp.bufr.BufrIosp2;
import ucar.nc2.iosp.bufr.Descriptor;
import ucar.nc2.ncml.NcMLReader;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.units.DateUnit;
import ucar.nc2.util.CancelTask;
import ucar.nc2.util.Indent;
import ucar.unidata.geoloc.EarthLocation;
import ucar.unidata.geoloc.Station;

import java.io.File;
import java.io.IOException;
import java.util.Formatter;
import java.util.List;

/**
 * Use BufrConfig to make BUFR files into PointFeatureDataset
 *
 * @author caron
 * @since 8/14/13
 */
public class BufrFeatureDatasetFactory implements FeatureDatasetFactory {
  static private org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BufrFeatureDatasetFactory.class);

  @Override
  public Object isMine(FeatureType wantFeatureType, NetcdfDataset ncd, Formatter errlog) throws IOException {
    IOServiceProvider iosp = ncd.getIosp();
    return (iosp != null && iosp instanceof BufrIosp2) ? true : null;
  }

  @Override
  public FeatureType[] getFeatureType() {
    return new FeatureType[]{FeatureType.ANY_POINT};
  }

  @Override
  public FeatureDataset open(FeatureType ftype, NetcdfDataset ncd, Object analysis, CancelTask task, Formatter errlog) throws IOException {
    /* BufrIosp2 iosp = (BufrIosp2) ncd.getIosp();
    BufrConfig config = iosp.getConfig();
    Formatter f = new Formatter();
    config.show(f);
    System.out.printf("%s%n", f);

    Element iospParam = iosp.getElem();
    if (iospParam != null) {
      Element parent = iospParam.getChild("bufr2nc", NcMLReader.ncNS);
      show(parent, new Indent(2));

      processSeq((Structure) ncd.findVariable(BufrIosp2.obsRecord), parent);
    }  */

    File indexFile = BufrCdmIndex.calcIndexFile(ncd.getLocation());
    BufrCdmIndex index = BufrCdmIndex.readIndex(indexFile.getPath());
    return new BufrStationDataset(ncd, index);
  }

  private void show(Element parent, Indent indent) {
    if (parent == null) return;
    for (Element child : parent.getChildren("fld", NcMLReader.ncNS)) {
      String idx = child.getAttributeValue("idx");
      String fxy = child.getAttributeValue("fxy");
      String name = child.getAttributeValue("name");
      String action = child.getAttributeValue("action");
      System.out.printf("%sidx='%s' fxy='%s' name='%s' action='%s'%n", indent, idx, fxy, name, action);
      indent.incr();
      show(child, indent);
      indent.decr();
    }
  }

  private void processSeq(Structure struct, Element parent) throws IOException {
    if (parent == null || struct == null) return;
    List<Variable> vars = struct.getVariables();
    for (Element child : parent.getChildren("fld", NcMLReader.ncNS)) {
      String idxS = child.getAttributeValue("idx");
      int idx = Integer.parseInt(idxS);
      if (idx < 0 || idx >= vars.size()) {
        log.error("Bad index = %s", child);
        continue;
      }
      Variable want = vars.get(idx);
      struct.removeMemberVariable(want);
      System.out.printf("removed %s%n", want);
    }
  }

  private class BufrStationDataset extends PointDatasetImpl {

    // LOOK - need to modify the list of Variables
    //public List<VariableSimpleIF> getDataVariables() {
    //  return null;
    //}

    private BufrStationDataset(NetcdfDataset ncfile, BufrCdmIndex index) {
      super(ncfile, FeatureType.STATION);

      BufrStationCollection bufrCollection = new BufrStationCollection(ncfile, index);
      setPointFeatureCollection(bufrCollection);
    }

    @Override
    public FeatureType getFeatureType() {
      return FeatureType.STATION;
    }

  }

  private class BufrStationCollection extends StationTimeSeriesCollectionImpl {
    BufrCdmIndex index;
    SequenceDS obs;
    DateUnit dateUnit;
    StandardFields.ExtractFromStructure extract;

    private BufrStationCollection(NetcdfDataset ncfile, BufrCdmIndex index) {
      super(ncfile.getLocation());
      this.index = index;

      this.obs = (SequenceDS) ncfile.findVariable(BufrIosp2.obsRecord);

      // need  the center id to match the standard fields
      Attribute centerAtt = ncfile.findGlobalAttribute(BufrIosp2.centerId);
      int center = (centerAtt == null) ? 0 : centerAtt.getNumericValue().intValue();
      this.extract = new StandardFields.ExtractFromStructure(center, obs);

      try {
         dateUnit = new DateUnit("msecs since 1970-01-01T00:00:00");
       } catch (Exception e) {
         e.printStackTrace();  //cant happen
       }
    }

    @Override
    protected void initStationHelper() {
      stationHelper = new StationHelper();
      for (BufrCdmIndexProto.Station s : index.stations)
        stationHelper.addStation( new BufrStation(s, dateUnit));
    }

    private class BufrStation extends StationFeatureImpl {
      private BufrStation(BufrCdmIndexProto.Station proto, DateUnit timeUnit) {
        super(proto.getId(), proto.getDesc(), proto.getWmoId(), proto.getLat(), proto.getLon(), proto.getAlt(), timeUnit, proto.getCount());
      }

      @Override
      public PointFeatureIterator getPointFeatureIterator(int bufferSize) throws IOException {
        return new BufrStationIterator(obs.getStructureIterator(), null);
      }

      // iterates over the records for this station
      public class BufrStationIterator extends PointIteratorFromStructureData {
        int countRecords = 0;

        public BufrStationIterator(StructureDataIterator structIter, PointFeatureIterator.Filter filter) throws IOException {
          super(structIter, filter);
        }

        @Override
        protected PointFeature makeFeature(int recnum, StructureData sdata) throws IOException {
          extract.extract(sdata);
          String stationId = extract.getStationId();
          //System.out.printf("  '%s' '%s' (%d) %n", s.getName(), stationId, countRecords++);
          //if (count > 10) return null;
          if (!stationId.equals(s.getName()))
            return null;
          CalendarDate date = extract.makeCalendarDate();
          return new BufrStationPoint(s, date.getMillis(), 0, dateUnit, sdata);  // LOOK  obsTime, nomTime
        }
      }

      public class BufrStationPoint extends PointFeatureImpl {
        StructureData sdata;

        public BufrStationPoint(EarthLocation location, double obsTime, double nomTime, DateUnit timeUnit, StructureData sdata) {
          super(location, obsTime, nomTime, timeUnit);
          this.sdata = sdata;
        }

        @Override
        public StructureData getData() throws IOException {
          return sdata;
        }
      }
    }

    // iterates once over all the records
    public class BufrRecordIterator extends PointIteratorFromStructureData {
      StationHelper stationsWanted;

      public BufrRecordIterator(StructureDataIterator structIter, PointFeatureIterator.Filter filter, StationHelper stationsWanted) throws IOException {
        super(structIter, filter);
        this.stationsWanted = stationsWanted;
      }

      @Override
      protected PointFeature makeFeature(int recnum, StructureData sdata) throws IOException {
        extract.extract(sdata);
        String stationId = extract.getStationId();
        Station want = stationsWanted.getStation(stationId);
        if (want == null)
          return null;
        CalendarDate date = extract.makeCalendarDate();
        return new BufrPoint(want, date.getMillis(), 0, dateUnit, sdata);  // LOOK  obsTime, nomTime
      }
    }

    public class BufrPoint extends PointFeatureImpl {
      StructureData sdata;

      public BufrPoint(EarthLocation location, double obsTime, double nomTime, DateUnit timeUnit, StructureData sdata) {
        super(location, obsTime, nomTime, timeUnit);
        this.sdata = sdata;
      }

      @Override
      public StructureData getData() throws IOException {
        return sdata;
      }
    }

  }
}