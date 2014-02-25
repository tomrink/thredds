/*
 * Copyright (c) 1998 - 2011. University Corporation for Atmospheric Research/Unidata
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting                                                             2
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.ui;

//import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
//import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import thredds.inventory.MFile;
import thredds.inventory.MFileCollectionManager;
import ucar.ma2.DataType;
import ucar.nc2.grib.*;
import ucar.nc2.grib.grib2.*;
import ucar.nc2.grib.grib2.table.Grib2Customizer;
import ucar.nc2.grib.grib2.table.NcepLocalTables;
import ucar.nc2.grib.grib2.table.WmoTemplateTable;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.ui.widget.*;
import ucar.nc2.ui.widget.PopupMenu;
import ucar.nc2.util.IO;
import ucar.nc2.util.Misc;
import ucar.nc2.wmo.CommonCodeTable;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.ui.BeanTable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Grib2 data reading
 *
 * @author caron
 * @since Sep 7, 2012
 */
public class Grib2DataPanel extends JPanel {
  static private Map<String, WmoTemplateTable> gribTemplates = null;
  static private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Grib2DataPanel.class);

  private PreferencesExt prefs;

  private BeanTable param2BeanTable, record2BeanTable;
  private JSplitPane split, split2;

  private TextHistoryPane infoPopup, infoPopup2, drsInfo;
  private IndependentWindow infoWindow, infoWindow2;
  private FileManager fileChooser;

  public Grib2DataPanel(PreferencesExt prefs) {
    this.prefs = prefs;

    PopupMenu varPopup;

    ////////////////
    param2BeanTable = new BeanTable(Grib2ParameterBean.class, (PreferencesExt) prefs.node("Param2Bean"), false,
            "UniquePDSVariables", "from Grib2Input.getRecords()", null);
    param2BeanTable.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        Grib2ParameterBean pb = (Grib2ParameterBean) param2BeanTable.getSelectedBean();
        if (pb != null)
          record2BeanTable.setBeans(pb.getRecordBeans());
      }
    });

    varPopup = new ucar.nc2.ui.widget.PopupMenu(param2BeanTable.getJTable(), "Options");
    varPopup.addAction("Show PDS", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Grib2ParameterBean pb = (Grib2ParameterBean) param2BeanTable.getSelectedBean();
        if (pb != null) {
          Formatter f = new Formatter();
          showPdsTemplate(pb.gr.getPDSsection(), f, cust);
          infoPopup2.setText(f.toString());
          infoPopup2.gotoTop();
          infoWindow2.show();
        }
      }
    });

    record2BeanTable = new BeanTable(Grib2RecordBean.class, (PreferencesExt) prefs.node("Record2Bean"), false,
            "DataRepresentation", "from Grib2Input.getRecords()", null);
    record2BeanTable.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        Grib2RecordBean pb = (Grib2RecordBean) record2BeanTable.getSelectedBean();
        if (pb != null)
          drsInfo.setText(pb.drs.toString());
      }
    });

    varPopup = new PopupMenu(record2BeanTable.getJTable(), "Options");

    varPopup.addAction("Compare GridRecord", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        List list = record2BeanTable.getSelectedBeans();
        if (list.size() == 2) {
          Grib2RecordBean bean1 = (Grib2RecordBean) list.get(0);
          Grib2RecordBean bean2 = (Grib2RecordBean) list.get(1);
          Formatter f = new Formatter();
          compare(bean1, bean2, f);
          infoPopup2.setText(f.toString());
          infoPopup2.gotoTop();
          infoWindow2.show();
        }
      }
    });

    varPopup.addAction("Show raw PDS bytes", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Formatter f = new Formatter();
        List list = record2BeanTable.getSelectedBeans();
        for (int i = 0; i < list.size(); i++) {
          Grib2RecordBean bean = (Grib2RecordBean) list.get(i);
          bean.toRawPdsString(f);
        }
        infoPopup.setText(f.toString());
        infoPopup.gotoTop();
        infoWindow.show();
      }
    });

    varPopup.addAction("Show complete GridRecord", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Grib2RecordBean bean = (Grib2RecordBean) record2BeanTable.getSelectedBean();
        if (bean != null) {
          Formatter f = new Formatter();
          try {
            showCompleteGribRecord(f, fileList.get(bean.gr.getFile()).getPath(), bean.gr, cust);
          } catch (IOException ioe) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(10000);
            ioe.printStackTrace(new PrintStream(bos));
            f.format("%s", bos.toString());
          }
          infoPopup.setText(f.toString());
          infoPopup.gotoTop();
          infoWindow.show();
        }
      }
    });

    varPopup.addAction("Show Processed GridRecord", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        List list = record2BeanTable.getSelectedBeans();
        Formatter f = new Formatter();
        for (Object o : list) {
          Grib2RecordBean bean = (Grib2RecordBean) o;
          bean.showProcessedGridRecord(f);
        }
        infoPopup2.setText(f.toString());
        infoPopup2.gotoTop();
        infoWindow2.show();
      }
    });

    varPopup.addAction("Compare Data", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        List list = record2BeanTable.getSelectedBeans();
        if (list.size() == 2) {
          Grib2RecordBean bean1 = (Grib2RecordBean) list.get(0);
          Grib2RecordBean bean2 = (Grib2RecordBean) list.get(1);
          Formatter f = new Formatter();
          compareData(bean1, bean2, f);
          infoPopup2.setText(f.toString());
          infoPopup2.gotoTop();
          infoWindow2.show();
        }
      }
    });

    varPopup.addAction("Show Data", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Grib2RecordBean bean = (Grib2RecordBean) record2BeanTable.getSelectedBean();
        if (bean != null) {
          Formatter f = new Formatter();
          showData(bean, f);
          infoPopup2.setText(f.toString());
          infoPopup2.gotoTop();
          infoWindow2.show();
        }
      }
    });

    varPopup.addAction("Extract GribRecord to File", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        List beans = record2BeanTable.getSelectedBeans();
        if (beans.size() > 0)
          writeToFile(beans);
      }
    });

    varPopup.addAction("Show Bitmap", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Grib2RecordBean bean = (Grib2RecordBean) record2BeanTable.getSelectedBean();
        if (bean != null) {
          Formatter f = new Formatter();
          try {
            showBitmap(bean, f);
          } catch (IOException e1) {
            e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
          }
          infoPopup2.setText(f.toString());
          infoPopup2.gotoTop();
          infoWindow2.show();
        }
      }
    });

    varPopup.addAction("Compute Scale/offset of data", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Grib2RecordBean bean = (Grib2RecordBean) record2BeanTable.getSelectedBean();
        if (bean != null) {
          Formatter f = new Formatter();
          calcData(bean, f);
          infoPopup2.setText(f.toString());
          infoPopup2.gotoTop();
          infoWindow2.show();
        }
      }
    });

    //gds2Table = new BeanTable(Gds2Bean.class, (PreferencesExt) prefs.node("Gds2Bean"), false, "Grib2GridDefinitionSection", "unique from Grib2Records");
    //varPopup = new PopupMenu(gds2Table.getJTable(), "Options");

    /////////////////////////////////////////
    // the info windows
    infoPopup = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("netcdfUI"), infoPopup);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 500, 300)));

    infoPopup2 = new TextHistoryPane();
    infoWindow2 = new IndependentWindow("Extra Information", BAMutil.getImage("netcdfUI"), infoPopup2);
    infoWindow2.setBounds((Rectangle) prefs.getBean("InfoWindowBounds2", new Rectangle(300, 300, 500, 300)));

    drsInfo = new TextHistoryPane();

    setLayout(new BorderLayout());

    split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, param2BeanTable, record2BeanTable);
    split2.setDividerLocation(prefs.getInt("splitPos2", 800));

    split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, drsInfo);
    split.setDividerLocation(prefs.getInt("splitPos", 500));

    add(split, BorderLayout.CENTER);

  }

  public void save() {
    param2BeanTable.saveState(false);
    record2BeanTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putBeanObject("InfoWindowBounds2", infoWindow2.getBounds());
    if (split != null) prefs.putInt("splitPos", split.getDividerLocation());
    if (split2 != null) prefs.putInt("splitPos2", split2.getDividerLocation());
  }

  ///////////////////////////////////////////////

  private String spec;
  private MFileCollectionManager dcm;
  private List<MFile> fileList;
  private Grib2Customizer cust;
  private Grib2Rectilyser rect2;

  /* public void setCollection(String filename) throws IOException {
    if (filename.endsWith(GribCollection.IDX_EXT)) {
      openIndex(filename);
    } else {
      openCollection(filename);
    }
  }

  private void openIndex(String filename) throws IOException {
    GribCollection gribCollection = new GribCollection(null);
    if (!gribCollection.readIndex(filename))
      throw new FileNotFoundException();

    this.cust = GribTables.factory(gribCollection.center, gribCollection.subcenter, gribCollection.master, gribCollection.local);

    java.util.List<Grib2ParameterBean> params = new ArrayList<Grib2ParameterBean>();
    java.util.List<Gds2Bean> gdsList = new ArrayList<Gds2Bean>();

    for (GribCollection.GroupHcs gHcs : gribCollection.getGroups()) {
      for (GribCollection.VariableIndex vi : gHcs.varIndex) {
        for (vi.records)
      }
    }
      addGroup(ncfile, g, useGroups);


    int fileno = 0;
    fileList = dcm.getFiles();
    for (MFile mfile : fileList) {
      f.format("%n %s%n", mfile.getPath());
      processGribFile(mfile.getPath(), fileno++, pdsSet, gdsSet, params);
    }
    param2BeanTable.setBeans(params);

    for (Grib2SectionGridDefinition gds : gdsSet.values()) {
      gdsList.add(new Gds2Bean( gds));
    }
    gds2Table.setBeans(gdsList);
  }  */

  public void setCollection(String spec) throws IOException {
    this.spec = spec;
    this.cust = null;
    this.rect2 = null;

    Formatter f = new Formatter();
    this.dcm = scanCollection(spec, f);
    if (dcm == null) {
      javax.swing.JOptionPane.showMessageDialog(this, "Collection is null\n" + f.toString());
      return;
    }

    Map<Integer, Grib2ParameterBean> pdsSet = new HashMap<Integer, Grib2ParameterBean>();
    Map<Integer, Grib2SectionGridDefinition> gdsSet = new HashMap<Integer, Grib2SectionGridDefinition>();

    java.util.List<Grib2ParameterBean> params = new ArrayList<Grib2ParameterBean>();

    int fileno = 0;
    for (MFile mfile : fileList) {
      f.format("%n %s%n", mfile.getPath());
      ucar.unidata.io.RandomAccessFile raf = new ucar.unidata.io.RandomAccessFile(mfile.getPath(), "r");
      processGribFile(mfile, fileno++, raf, pdsSet, gdsSet, params, f);
      raf.close();
    }
    param2BeanTable.setBeans(params);
  }

  private void processGribFile(MFile mfile, int fileno, ucar.unidata.io.RandomAccessFile raf,
                               Map<Integer, Grib2ParameterBean> pdsSet,
                               Map<Integer, Grib2SectionGridDefinition> gdsSet,
                               List<Grib2ParameterBean> params, Formatter f) throws IOException {

    Grib2Index index = new Grib2Index();
    if (!index.readIndex(mfile.getPath(), mfile.getLastModified())) {
      index.makeIndex(mfile.getPath(), null);
    }

    for (Grib2SectionGridDefinition gds : index.getGds()) {
      int hash = gds.getGDS().hashCode();
      if (gdsSet.get(hash) == null)
        gdsSet.put(hash, gds);
    }

    for (Grib2Record gr : index.getRecords()) {
      gr.setFile(fileno);

      if (rect2 == null) {
        cust = Grib2Customizer.factory(gr);
        rect2 = new Grib2Rectilyser(cust, null, 0, null);
      }

      int id = rect2.cdmVariableHash(gr, 0);
      Grib2ParameterBean bean = pdsSet.get(id);
      if (bean == null) {
        bean = new Grib2ParameterBean(gr);
        pdsSet.put(id, bean);
        params.add(bean);
      }
      bean.addRecord(gr, raf);
    }
  }

  private MFileCollectionManager scanCollection(String spec, Formatter f) {
    MFileCollectionManager dc = null;
    try {
      dc = MFileCollectionManager.open(spec, null, f);
      dc.scan(false);
      fileList = (List<MFile>) Misc.getList(dc.getFiles());
      return dc;

    } catch (Exception e) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(10000);
      e.printStackTrace(new PrintStream(bos));
      f.format("%s", bos.toString());
      return null;
    }
  }

  public void runAggregator(Formatter f) throws IOException {
    List<Grib2Record> records = new ArrayList<Grib2Record>();
    List<String> filenames = new ArrayList<String>();

    int fileno = 0;
    for (MFile mfile : dcm.getFiles()) {
      f.format("%3d: %s%n", fileno, mfile.getPath());
      filenames.add(mfile.getPath());

      Grib2Index index = new Grib2Index();
      if (!index.readIndex(mfile.getPath(), mfile.getLastModified())) {
        index.makeIndex(mfile.getPath(), null);
      }

      for (Grib2Record gr : index.getRecords()) {
        gr.setFile(fileno);
        records.add(gr);
      }
      fileno++;
    }

    Grib2Rectilyser.Counter stats = new Grib2Rectilyser.Counter();
    Grib2Rectilyser agg = new Grib2Rectilyser(cust, records, 0, null);
    agg.make(stats, null);
    agg.dump(f, cust);
    stats.recordsTotal = records.size();

    f.format("%s", stats.show());
  }

  /* public void runCollate(Formatter f) throws IOException {
    DatasetCollectionManager dcm = getCollection(spec, f);
    GribCollection gc = GribCollectionBuilder.factory(dcm);
    ArrayList<String> filenames = new ArrayList<String>();
    List<GribCollection.Group> groups = gc.makeAggregatedGroups(filenames, f);

    for (GribCollection.Group g : groups) {
      f.format("====================================================%n");
      f.format("Group %s%n", g.name);
      g.rect.dump(f, cust);
    }
  } */

  public boolean writeIndex(Formatter f) throws IOException {
    MFileCollectionManager dcm = scanCollection(spec, f);

    if (fileChooser == null)
      fileChooser = new FileManager(null, null, null, (PreferencesExt) prefs.node("FileManager"));
    String name = dcm.getCollectionName();
    int pos = name.lastIndexOf('/');
    if (pos < 0) pos = name.lastIndexOf('\\');
    if (pos > 0) name = name.substring(pos + 1);
    File def = new File(dcm.getRoot(), name + GribCollection.NCX_IDX);
    String filename = fileChooser.chooseFilename(def);
    if (filename == null) return false;
    if (!filename.endsWith(GribCollection.NCX_IDX))
      filename += GribCollection.NCX_IDX;
    File idxFile = new File(filename);

    Grib2CollectionBuilder.writeIndexFile(idxFile, dcm, logger);
    return true;
  }

  public void showCollection(Formatter f) {
    if (dcm == null) {
      if (spec == null) return;
      dcm = scanCollection(spec, f);
      if (dcm == null) return;
    }

    // just a list of the files
    f.format("dcm = %s%n", dcm);
    for (MFile mfile : dcm.getFiles()) {
      f.format("  %s%n", mfile.getPath());
    }

    // divided by group
    Map<Integer, Set<Integer>> groups = new HashMap<Integer, Set<Integer>>();
    for (Object o : param2BeanTable.getBeans()) {
      Grib2ParameterBean p = (Grib2ParameterBean) o;
      Set<Integer> group = groups.get(p.getGDS());
      if (group == null) {
        group = new TreeSet<Integer>();
        groups.put(p.getGDS(), group);
      }
      for (Grib2RecordBean r : p.getRecordBeans())
        group.add(r.gr.getFile());
    }
  }

  public void checkProblems(Formatter f) {
    checkDuplicates(f);
    //checkRuntimes(f);
  }

  private class DateCount implements Comparable<DateCount> {
    CalendarDate d;
    int count;

    private DateCount(CalendarDate d) {
      this.d = d;
    }

    @Override
    public int compareTo(DateCount o) {
      return d.compareTo(o.d);
    }
  }

  /* private void checkRuntimes(Formatter f) {
     Map<Date, DateCount> runs = new HashMap<Date, DateCount>();
     List<Grib2ParameterBean> params = param2BeanTable.getBeans();
     for (Grib2ParameterBean pb : params) {
       List<Grib2RecordBean> records = pb.getRecordBeans();
       for (Grib2RecordBean record : records) {
         Date d = record.getBaseTime();
         DateCount dc = runs.get(d);
         if (dc == null) {
           dc = new DateCount(d);
           runs.put(d, dc);
         }
         dc.count++;
       }
     }

     List<DateCount> dcList= new ArrayList<DateCount>(runs.values());
     Collections.sort(dcList);

     f.format("Run Dates%n");
     for (DateCount dc : dcList)
       f.format(" %s == %d%n", df.toDateTimeStringISO( dc.d), dc.count);
   } */

  private void checkDuplicates(Formatter f) {

    // how unique are the pds ?
    Set<Long> pdsMap = new HashSet<Long>();
    int dups = 0;
    int count = 0;

    // do all records have the same runtime ?
    Map<CalendarDate, DateCount> dateMap = new HashMap<CalendarDate, DateCount>();

    List<Grib2ParameterBean> params = param2BeanTable.getBeans();
    for (Grib2ParameterBean param : params) {
      for (Grib2RecordBean record : param.getRecordBeans()) {
        CalendarDate d = record.gr.getReferenceDate();
        DateCount dc = dateMap.get(d);
        if (dc == null) {
          dc = new DateCount(d);
          dateMap.put(d, dc);
        }
        dc.count++;

        Grib2SectionProductDefinition pdss = record.gr.getPDSsection();
        long crc = pdss.calcCRC();
        if (pdsMap.contains(crc))
          dups++;
        else
          pdsMap.add(crc);
        count++;
      }
    }

    f.format("PDS duplicates = %d / %d%n%n", dups, count);

    List<DateCount> dcList = new ArrayList<DateCount>(dateMap.values());
    Collections.sort(dcList);

    f.format("Run Dates%n");
    int total = 0;
    for (DateCount dc : dcList) {
      f.format(" %s == %d%n", dc.d, dc.count);
      total += dc.count;
    }
    f.format("total records = %d%n", total);
  }

  private void writeToFile(List beans) {

    if (fileChooser == null)
      fileChooser = new FileManager(null, null, null, (PreferencesExt) prefs.node("FileManager"));

    FileOutputStream fos = null;
    RandomAccessFile raf = null;

    try {
      String filename = null;
      boolean append = false;
      int n = 0;
      MFile curr = null;

      for (Object o : beans) {
        Grib2RecordBean bean = (Grib2RecordBean) o;
        MFile mfile = fileList.get(bean.gr.getFile());
        if (curr == null || curr != mfile) {
          if (raf != null) raf.close();
          raf = new RandomAccessFile(mfile.getPath(), "r");
          curr = mfile;
        }

        if (fos == null) {
          String defloc = mfile.getPath();
          filename = fileChooser.chooseFilenameToSave(defloc + ".grib2");
          if (filename == null) return;
          File f = new File(filename);
          append = f.exists();
          fos = new FileOutputStream(filename, append);
        }

        Grib2SectionIndicator is = bean.gr.getIs();
        int size = (int) (is.getMessageLength());
        long startPos = is.getStartPos();
        if (startPos < 0) {
          JOptionPane.showMessageDialog(Grib2DataPanel.this, "Old index does not have message start - record not written");
        }

        byte[] rb = new byte[size];
        raf.seek(startPos);
        raf.readFully(rb);
        fos.write(rb);
        n++;
      }

      JOptionPane.showMessageDialog(Grib2DataPanel.this, filename + ": " + n + " records successfully written, append=" + append);

    } catch (Exception ex) {
      JOptionPane.showMessageDialog(Grib2DataPanel.this, "ERROR: " + ex.getMessage());
      ex.printStackTrace();

    } finally {
      try {
        if (fos != null) fos.close();
        if (raf != null) raf.close();
      } catch (IOException ioe) {
      }
    }
  }


  private void compare(Grib2RecordBean bean1, Grib2RecordBean bean2, Formatter f) {
    Grib2SectionIndicator is1 = bean1.gr.getIs();
    Grib2SectionIndicator is2 = bean2.gr.getIs();
    f.format("Indicator Section%n");
    if (is1.getDiscipline() != is2.getDiscipline())
      f.format("getDiscipline differs %d != %d %n", is1.getDiscipline(), is2.getDiscipline());
    if (is1.getMessageLength() != is2.getMessageLength())
      f.format("getGribLength differs %d != %d %n", is1.getMessageLength(), is2.getMessageLength());

    f.format("%nId Section%n");
    Grib2SectionIdentification id1 = bean1.gr.getId();
    Grib2SectionIdentification id2 = bean2.gr.getId();
    if (id1.getCenter_id() != id2.getCenter_id())
      f.format("Center_id differs %d != %d %n", id1.getCenter_id(), id2.getCenter_id());
    if (id1.getSubcenter_id() != id2.getSubcenter_id())
      f.format("Subcenter_id differs %d != %d %n", id1.getSubcenter_id(), id2.getSubcenter_id());
    if (id1.getMaster_table_version() != id2.getMaster_table_version())
      f.format("Master_table_version differs %d != %d %n", id1.getMaster_table_version(), id2.getMaster_table_version());
    if (id1.getLocal_table_version() != id2.getLocal_table_version())
      f.format("Local_table_version differs %d != %d %n", id1.getLocal_table_version(), id2.getLocal_table_version());
    if (id1.getProductionStatus() != id2.getProductionStatus())
      f.format("ProductionStatus differs %d != %d %n", id1.getProductionStatus(), id2.getProductionStatus());
    if (id1.getTypeOfProcessedData() != id2.getTypeOfProcessedData())
      f.format("TypeOfProcessedData differs %d != %d %n", id1.getTypeOfProcessedData(), id2.getTypeOfProcessedData());
    if (!id1.getReferenceDate().equals(id2.getReferenceDate()))
      f.format("ReferenceDate differs %s != %s %n", id1.getReferenceDate(), id2.getReferenceDate());
    if (id1.getSignificanceOfRT() != id2.getSignificanceOfRT())
      f.format("getSignificanceOfRT differs %d != %d %n", id1.getSignificanceOfRT(), id2.getSignificanceOfRT());


    Grib2SectionLocalUse lus1 = bean1.gr.getLocalUseSection();
    Grib2SectionLocalUse lus2 = bean2.gr.getLocalUseSection();
    if (lus1 == null || lus2 == null) {
      if (lus1 == lus2)
        f.format("%nLus are both null%n");
      else
        f.format("%nLus are different %s != %s %n", lus1, lus2);
    } else {
      f.format("%nCompare LocalUseSection%n");
      Misc.compare(lus1.getRawBytes(), lus2.getRawBytes(), f);
    }

    compare(bean1.gr.getPDSsection(), bean2.gr.getPDSsection(), f);
    compare(bean1.gr.getGDSsection(), bean2.gr.getGDSsection(), f);
  }

  private void compare(Grib2SectionGridDefinition gdss1, Grib2SectionGridDefinition gdss2, Formatter f) {
    f.format("1 GribGDS hash = %s%n", gdss1.getGDS().hashCode());
    f.format("2 GribGDS hash = %s%n", gdss2.getGDS().hashCode());

    f.format("%nCompare Gds%n");
    byte[] raw1 = gdss1.getRawBytes();
    byte[] raw2 = gdss2.getRawBytes();
    Misc.compare(raw1, raw2, f);

    Grib2Gds gds1 = gdss1.getGDS();
    Grib2Gds gds2 = gdss2.getGDS();
    GdsHorizCoordSys gdsh1 = gds1.makeHorizCoordSys();
    GdsHorizCoordSys gdsh2 = gds2.makeHorizCoordSys();

    f.format("%ncompare gds1 - gds22%n");
    f.format(" Start x diff : %f%n", gdsh1.getStartX() - gdsh2.getStartX());
    f.format(" Start y diff : %f%n", gdsh1.getStartY() - gdsh2.getStartY());
    f.format(" End x diff : %f%n", gdsh1.getEndX() - gdsh2.getEndX());
    f.format(" End y diff : %f%n", gdsh1.getEndY() - gdsh2.getEndY());

    LatLonPoint pt1 = gdsh1.getCenterLatLon();
    LatLonPoint pt2 = gdsh2.getCenterLatLon();
    f.format(" Center lon diff : %f%n", pt1.getLongitude() - pt2.getLongitude());
    f.format(" Center lat diff : %f%n", pt1.getLatitude() - pt2.getLatitude());
  }


  private void compare(Grib2SectionProductDefinition pds1, Grib2SectionProductDefinition pds2, Formatter f) {
    f.format("%nCompare Pds%n");
    byte[] raw1 = pds1.getRawBytes();
    byte[] raw2 = pds2.getRawBytes();
    Misc.compare(raw1, raw2, f);
  }

  void compareData(Grib2RecordBean bean1, Grib2RecordBean bean2, Formatter f) {
    float[] data1 = null, data2 = null;
    try {
      data1 = bean1.readData();
      data2 = bean2.readData();
    } catch (IOException e) {
      f.format("IOException %s", e.getMessage());
      return;
    }

    Misc.compare(data1, data2, f);
  }

  void showData(Grib2RecordBean bean1, Formatter f) {
    float[] data;
    try {
      data = bean1.readData();
    } catch (IOException e) {
      f.format("IOException %s", e.getMessage());
      return;
    }

    for (float fd : data)
      f.format("%f%n", fd);
  }

  void showBitmap(Grib2RecordBean bean1, Formatter f) throws IOException {
    byte[] bitmap;
    ucar.unidata.io.RandomAccessFile raf = null;
    try {
      raf = bean1.getRaf();
      Grib2SectionBitMap bms = bean1.gr.getBitmapSection();
      f.format("%s%n", bms);
      bitmap = bms.getBitmap(raf);
    } finally {
      if (raf != null) raf.close();
    }

    if (bitmap == null) {
      f.format(" no bitmap%n");
      return;
    }

    int count = 0;
    int bits = 0;
    for (byte b : bitmap) {
      short s = DataType.unsignedByteToShort(b);
      bits += Long.bitCount(s);
      f.format("%8s", Long.toBinaryString(s));
      if (++count % 10 == 0)
        f.format("%n");
    }
    f.format("%n%n#bits on = %d%n", bits);
  }

  void calcData(Grib2RecordBean bean1, Formatter f) {
    float[] data;
    try {
      data = bean1.readData();
    } catch (IOException e) {
      f.format("IOException %s", e.getMessage());
      return;
    }

    // calc scale/offset

    int nbits = bean1.getNBits();
    //int width = (2 << nbits) - 1;
    int width2 = (2 << (nbits-1)) - 1;
    f.format(" nbits = %d%n", nbits);
    //f.format(" width = %d (0x%s) %n", width, Long.toHexString(width));
    f.format(" width = %d (0x%s) %n", width2, Long.toHexString(width2));

    float dataMin = Float.MAX_VALUE;
    float dataMax = -Float.MAX_VALUE;
    for (float fd : data) {
      dataMin = Math.min(dataMin, fd);
      dataMax = Math.max(dataMax, fd);
    }
    f.format(" dataMin = %f%n", dataMin);
    f.format(" dataMax = %f%n", dataMax);
    f.format(" range = %f%n", (dataMax - dataMin));

    // scale_factor =(dataMax - dataMin) / (2^n - 1)
    // add_offset = dataMin + 2^(n-1) * scale_factor

    float scale_factor = (dataMax - dataMin) / width2;
    float add_offset = dataMin + width2 * scale_factor / 2;

    f.format(" scale_factor = %f%n", scale_factor);
    f.format(" add_offset = %f%n", add_offset);

    // unpacked_data_value = packed_data_value * scale_factor + add_offset
    // packed_data_value = nint((unpacked_data_value - add_offset) / scale_factor)

    int n = data.length;

    ByteBuffer bb = ByteBuffer.allocate(2*n);
    ShortBuffer sb = bb.asShortBuffer();
    float diffMax = -Float.MAX_VALUE;
    float diffTotal = 0;
    float diffTotal2 = 0;
    for (float fd : data) {
      short packed_data = (short) Math.round((fd - add_offset) / scale_factor);
      float unpacked_data = packed_data * scale_factor + add_offset;
      float diff = Math.abs(fd-unpacked_data);
      if (diff > 100)
        f.format("   org=%f, packed_data=%d unpacked=%f diff = %f%n",fd, packed_data, unpacked_data, diff);

      diffMax = Math.max(diffMax, diff);
      diffTotal += diff;
      diffTotal2 += diff*diff;
      sb.put(packed_data);
    }

    f.format("%n max_diff = %f%n", diffMax);
    f.format(" avg_diff = %f%n", diffTotal/data.length);

    // Math.sqrt( sumsq/n - avg * avg)
    float mean = diffTotal/n;
    float var = (diffTotal2/n - mean * mean);
    f.format(" std_diff = %f%n", Math.sqrt(var));

    f.format("%nCompression%n");
    f.format(" number of values = %d%n", n);
    f.format(" uncompressed as floats = %d%n", n*4);
    f.format(" uncompressed packed = %d%n", n*nbits/8);
    f.format(" grib compressed = %d%n",  bean1.getDataLength());

    f.format("%ndeflate%n");
    Deflater deflater = new Deflater();
    deflater.setInput(bb.array());
    deflater.finish();
    int compressedSize = deflater.deflate(new byte[10*n]);
    deflater.end();

    f.format(" compressedSize = %d%n", compressedSize);
    f.format(" compressedRatio = %f%n", (float) compressedSize / (n*nbits/8));
    f.format(" ratio with grib = %f%n", (float) compressedSize / bean1.getDataLength());

    try {
      f.format("%nbzip2%n");
      ByteArrayOutputStream out = new ByteArrayOutputStream(2*compressedSize);
      org.itadaki.bzip2.BZip2OutputStream zipper = new org.itadaki.bzip2.BZip2OutputStream(out);
      InputStream fin = new ByteArrayInputStream(bb.array());
      IO.copy(fin, zipper);
      zipper.close();
      compressedSize = out.size();
      f.format(" compressedSize = %d%n", compressedSize);
      f.format(" compressedRatio = %f%n", (float) compressedSize / (n*nbits/8));
      f.format(" ratio with grib = %f%n", (float) compressedSize / bean1.getDataLength());

    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

    /* try {
      f.format("%nbzip2%n");
      ByteArrayOutputStream out = new ByteArrayOutputStream(2*compressedSize);
      BZip2CompressorOutputStream zipper = new BZip2CompressorOutputStream(out);
      InputStream fin = new ByteArrayInputStream(bb.array());
      IO.copy(fin, zipper);
      zipper.close();
      compressedSize = out.size();
      f.format(" compressedSize = %d%n", compressedSize);
      f.format(" compressedRatio = %f%n", (float) compressedSize / (n*nbits/8));
      f.format(" ratio with grib = %f%n", (float) compressedSize / bean1.getDataLength());

    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

    try {
      f.format("%nLZMA2%n");
      ByteArrayOutputStream out = new ByteArrayOutputStream(2*compressedSize);
      XZCompressorOutputStream zipper = new XZCompressorOutputStream(out);
      InputStream fin = new ByteArrayInputStream(bb.array());
      IO.copy(fin, zipper);
      zipper.close();
      compressedSize = out.size();
      f.format(" compressedSize = %d%n", compressedSize);
      f.format(" compressedRatio = %f%n", (float) compressedSize / (n*nbits/8));
      f.format(" ratio with grib = %f%n", (float) compressedSize / bean1.getDataLength());

    } catch (IOException ioe) {
      ioe.printStackTrace();
    } */

  }



  ////////////////////////////////////////////////////////////////////////////////////////////////

  /*
  /* private String doAccumAlgo(Grib2ParameterBean pb) {
    List<Grib2RecordBean> all = new ArrayList<Grib2RecordBean>(pb.getRecordBeans());
    List<Grib2RecordBean> hourAccum = new ArrayList<Grib2RecordBean>(all.size());
    List<Grib2RecordBean> runAccum = new ArrayList<Grib2RecordBean>(all.size());

    Set<Integer> ftimes = new HashSet<Integer>();

    for (Grib2RecordBean rb : pb.getRecordBeans()) {
      int ftime = rb.getForecastTime();
      ftimes.add(ftime);

      int start = rb.getStartInterval();
      int end = rb.getEndInterval();
      if (end - start == 1) hourAccum.add(rb);
      if (start == 0) runAccum.add(rb);
    }

    int n = ftimes.size();

    Formatter f = new Formatter();
    f.format("      all: ");
    showList(all, f);
    f.format("%n");

    if (hourAccum.size() > n - 2) {
      for (Grib2RecordBean rb : hourAccum) all.remove(rb);
      f.format("hourAccum: ");
      showList(hourAccum, f);
      f.format("%n");
    }

    if (runAccum.size() > n - 2) {
      for (Grib2RecordBean rb : runAccum) all.remove(rb);
      f.format(" runAccum: ");
      showList(runAccum, f);
      f.format("%n");
    }

    if (all.size() > 0) {
      boolean same = true;
      int intv = -1;
      for (Grib2RecordBean rb : all) {
        int start = rb.getStartInterval();
        int end = rb.getEndInterval();
        int intv2 = end - start;
        if (intv < 0) intv = intv2;
        else same = (intv == intv2);
        if (!same) break;
      }

      f.format("remaining: ");
      showList(all, f);
      f.format("%s %n", same ? " Interval=" + intv : " Mixed");
    }

    return f.toString();
  }

  private void showList(List<Grib2RecordBean> list, Formatter f) {
    f.format("(%d) ", list.size());
    for (Grib2RecordBean rb : list)
      f.format(" %d-%d", rb.getStartInterval(), rb.getEndInterval());
  }  */

  ////////////////////////////////////////////////////////////////////////////

  public class Grib2ParameterBean {
    Grib2Record gr;
    Grib2SectionIdentification id;
    Grib2Pds pds;
    List<Grib2RecordBean> records;
    int discipline;
    //long gdsKey;

    // no-arg constructor

    public Grib2ParameterBean() {
    }

    public Grib2ParameterBean(Grib2Record r) throws IOException {
      this.gr = r;

      // long refTime = r.getId().getReferenceDate().getMillis();
      pds = r.getPDSsection().getPDS();
      id = r.getId();
      discipline = r.getDiscipline();
      records = new ArrayList<Grib2RecordBean>();
      //gdsKey = r.getGDSsection().calcCRC();
    }

    void addRecord(Grib2Record r, ucar.unidata.io.RandomAccessFile raf) throws IOException {
      records.add(new Grib2RecordBean(r, raf));
    }

    List<Grib2RecordBean> getRecordBeans() {
      return records;
    }

    public String getParamNo() {
      return discipline + "-" + pds.getParameterCategory() + "-" + pds.getParameterNumber();
    }

    public int getPDS() {
      return gr.getPDSsection().getPDSTemplateNumber();
    }

    public int getN() {
      return records.size();
    }

    public int getLevelType() {
      return pds.getLevelType1();
    }

    public String getLevelName() {
      return cust.getLevelNameShort(pds.getLevelType1());
    }

    public int getGDS() {
      return gr.getGDSsection().getGDS().hashCode();
    }

    public String toString() {
      Formatter f = new Formatter();
      showPdsTemplate(gr.getPDSsection(), f, cust);
      return f.toString();
    }

    public String toProcessedString() {
      Formatter f = new Formatter();
      showProcessedPds(gr, pds, discipline, f);
      return f.toString();
    }

    private float nbits = -1;
    private float avgbits;
    private float compress;
    public float getNBits() {
      calcBits();
      return nbits;
    }

    public float getAvgBits() {
      calcBits();
      return avgbits;
    }

    public float getCompress() {
      calcBits();
      return compress;
    }

    private void calcBits() {
      if (nbits >= 0) return;
      nbits = 0;
      int count = 0;
      for (Grib2RecordBean bean : records) {
        nbits += bean.getNBits();
        avgbits += bean.getAvgBits();
        count++;
      }
      compress = nbits / avgbits;
      nbits /= count;
      avgbits /= count;
    }


    ///////////////

    public String getName() {
      return GribUtils.makeNameFromDescription(cust.getVariableName(gr));
    }

    public String getUnits() {
      Grib2Customizer.Parameter p = cust.getParameter(discipline, pds.getParameterCategory(), pds.getParameterNumber());
      return (p == null) ? "?" : p.getUnit();
    }

    public final String getCenter() {
      //String center = CommonCodeTable.getCenterName(id.getCenter_id(), 2);
      //String subcenter = cust.getSubCenterName(id.getCenter_id(), id.getSubcenter_id());
      return id.getCenter_id() + "/" + id.getSubcenter_id(); // + " (" + center + "/" + subcenter + ")";
    }

    public final String getTable() {
      return id.getMaster_table_version() + "-" + id.getLocal_table_version();
    }

  }

  ////////////////////////////////////////////////////

  static void showBytes(Formatter f, byte[] buff) {
    for (byte b : buff) {
      int ub = (b < 0) ? b + 256 : b;
      if (b >= 32 && b < 127)
        f.format("%s", (char) ub);
      else
        f.format("(%d)", ub);
    }
  }

  static public void showCompleteGribRecord(Formatter f, String path, Grib2Record gr, Grib2Customizer cust) throws IOException {
    f.format("File=%d %s %n", gr.getFile(), path);
    f.format("Header=\"");
    showBytes(f, gr.getHeader());
    f.format("\"%n%n");
    int d = gr.getDiscipline();
    f.format("Grib2IndicatorSection%n");
    f.format(" Discipline = (%d) %s%n", d, cust.getTableValue("0.0", d));
    f.format(" Length     = %d%n", gr.getIs().getMessageLength());

    Grib2SectionIdentification id = gr.getId();
    f.format("%nGrib2IdentificationSection%n");
    f.format(" Center        = (%d) %s%n", id.getCenter_id(), CommonCodeTable.getCenterName(id.getCenter_id(), 2));
    f.format(" SubCenter     = (%d) %s%n", id.getSubcenter_id(), cust.getSubCenterName(id.getCenter_id(), id.getSubcenter_id()));
    f.format(" Master Table  = %d%n", id.getMaster_table_version());
    f.format(" Local Table   = %d%n", id.getLocal_table_version());
    f.format(" RefTimeSignif = %d (%s)%n", id.getSignificanceOfRT(), cust.getTableValue("1.2", id.getSignificanceOfRT()));
    f.format(" RefTime       = %s%n", id.getReferenceDate());
    f.format(" RefTime Fields = %d-%d-%d %d:%d:%d%n", id.getYear(), id.getMonth(), id.getDay(), id.getHour(), id.getMinute(), id.getSecond());
    f.format(" ProductionStatus      = %d (%s)%n", id.getProductionStatus(), cust.getTableValue("1.3", id.getProductionStatus()));
    f.format(" TypeOfProcessedData   = %d (%s)%n", id.getTypeOfProcessedData(), cust.getTableValue("1.4", id.getTypeOfProcessedData()));

    if (gr.hasLocalUseSection()) {
      byte[] lus = gr.getLocalUseSection().getRawBytes();
      f.format("%nLocal Use Section (grib section 2)%n");
      /* try {
       f.format(" String= %s%n", new String(lus, 0, lus.length, "UTF-8"));
     } catch (UnsupportedEncodingException e) {
       e.printStackTrace();
     } */
      f.format("bytes (len=%d) =", lus.length);
      Misc.showBytes(lus, f);
      f.format("%n");
    }

    Grib2SectionGridDefinition gds = gr.getGDSsection();
    Grib2Gds ggds = gds.getGDS();
    f.format("%nGrib2GridDefinitionSection hash=%d crc=%d%n", ggds.hashCode(), gds.calcCRC());
    f.format(" Length             = %d%n", gds.getLength());
    f.format(" Source  (3.0)      = %d (%s) %n", gds.getSource(), cust.getTableValue("3.0", gds.getSource()));
    f.format(" Npts               = %d%n", gds.getNumberPoints());
    f.format(" Template (3.1)     = %d%n", gds.getGDSTemplateNumber());
    showGdsTemplate(gds, f, cust);

    Grib2SectionProductDefinition pdss = gr.getPDSsection();
    f.format("%nGrib2ProductDefinitionSection%n");
    Grib2Pds pds = pdss.getPDS();
    if (pds.isTimeInterval()) {
      TimeCoord.TinvDate intv = cust.getForecastTimeInterval(gr);
      if (intv != null) f.format(" Interval     = %s%n", intv);
    }
    showPdsTemplate(pdss, f, cust);
    if (pds.getExtraCoordinatesCount() > 0) {
      float[] coords = pds.getExtraCoordinates();
      f.format("Hybrid Coordinates (%d) %n  ", coords.length);
      for (float fc : coords) f.format("%10.5f ", fc);
      f.format("%n%n");
    }

    Grib2SectionDataRepresentation drs = gr.getDataRepresentationSection();
    f.format("%nGrib2SectionDataRepresentation%n");
    f.format("  Template           = %d (%s) %n", drs.getDataTemplate(), cust.getTableValue("5.0", drs.getDataTemplate()));
    f.format("  NPoints            = %d%n", drs.getDataPoints());

    Grib2SectionData ds = gr.getDataSection();
    f.format("%nGrib2SectionData%n");
    f.format("  Starting Pos       = %d %n", ds.getStartingPosition());
    f.format("  Data Length        = %d%n", ds.getMsgLength());
  }

  static private void showGdsTemplate(Grib2SectionGridDefinition gds, Formatter f, Grib2Customizer cust) {
    int template = gds.getGDSTemplateNumber();
    byte[] raw = gds.getRawBytes();
    showRawWithTemplate("3." + template, raw, f, cust);
  }

  static private void showPdsTemplate(Grib2SectionProductDefinition pdss, Formatter f, Grib2Customizer cust) {
    int template = pdss.getPDSTemplateNumber();
    byte[] raw = pdss.getRawBytes();
    showRawWithTemplate("4." + template, raw, f, cust);
  }


  static private void showRawWithTemplate(String key, byte[] raw, Formatter f, Grib2Customizer cust) {
    if (gribTemplates == null)
      try {
        gribTemplates = WmoTemplateTable.getWmoStandard().map;
      } catch (IOException e) {
        f.format("Read template failed = %s%n", e.getMessage());
        return;
      }

    WmoTemplateTable gt = gribTemplates.get(key);
    if (gt == null)
      f.format("Cant find template %s%n", key);
    else
      gt.showInfo(cust, raw, f);
  }

  ////////////////////////////////////////////////////////
  private void showProcessedPds(Grib2Record gr, Grib2Pds pds, int discipline, Formatter f) {
    int template = pds.getTemplateNumber();
    f.format(" Product Template %3d = %s%n", template, cust.getTableValue("4.0", template));
    f.format(" Discipline %3d     = %s%n", discipline, cust.getTableValue("0.0", discipline));
    f.format(" Category %3d       = %s%n", pds.getParameterCategory(), cust.getCategory(discipline, pds.getParameterCategory()));
    Grib2Customizer.Parameter entry = cust.getParameter(discipline, pds.getParameterCategory(), pds.getParameterNumber());
    if (entry != null) {
      f.format(" Parameter Name     = %3d %s %n", pds.getParameterNumber(), entry.getName());
      f.format(" Parameter Units    = %s %n", entry.getUnit());
    } else {
      f.format(" Unknown Parameter  = %d-%d-%d %n", discipline, pds.getParameterCategory(), pds.getParameterNumber());
      cust.getParameter(discipline, pds.getParameterCategory(), pds.getParameterNumber()); // debug
    }
    f.format(" Parameter Table  = %s%n", cust.getTablePath(discipline, pds.getParameterCategory(), pds.getParameterNumber()));

    int tgp = pds.getGenProcessType();
    f.format(" Generating Process Type = %3d %s %n", tgp, cust.getTableValue("4.3", tgp));
    f.format(" Forecast Offset    = %3d %n", pds.getForecastTime());
    f.format(" First Surface Type = %3d %s %n", pds.getLevelType1(), cust.getLevelNameShort(pds.getLevelType1()));
    f.format(" First Surface value= %3f %n", pds.getLevelValue1());
    f.format(" Second Surface Type= %3d %s %n", pds.getLevelType2(), cust.getLevelNameShort(pds.getLevelType2()));
    f.format(" Second Surface val = %3f %n", pds.getLevelValue2());
    f.format("%n Level Name (from table 4.5) = %3s %n", cust.getTableValue("4.5", pds.getLevelType1()));
    f.format(" Gen Process Ttype (from table 4.3) = %3s %n", cust.getTableValue("4.3", pds.getGenProcessType()));
  }

  public class Grib2RecordBean {
    Grib2Record gr;
    Grib2Pds pds;
    Grib2Drs drs;
    Grib2SectionData dataSection;
    long drsLength;

    public Grib2RecordBean() {
    }

    public Grib2RecordBean(Grib2Record m, ucar.unidata.io.RandomAccessFile raf) throws IOException {
      this.gr = m;
      this.pds = gr.getPDSsection().getPDS();
      Grib2SectionDataRepresentation drss = gr.getDataRepresentationSection();
      this.drs = drss.getDrs(raf);
      this.drsLength = drss.getLength(raf);
      this.dataSection = gr.getDataSection();
    }

    public String getHeader() {
      return Grib2Utils.cleanupHeader(gr.getHeader());
    }

    public int getDrsTemplate() {
      return gr.getDataRepresentationSection().getDataTemplate();
    }

    public int getNDataPoints() {
      return gr.getDataRepresentationSection().getDataPoints();
    }

    public long getDrsLength() {
      return drsLength;
    }

    public int getDataLength() {
      return dataSection.getMsgLength();
    }

    public long getDataStart() {
      return dataSection.getStartingPosition();
    }

    public int getNBits() {
      return drs.getNBits();
    }

    public int getNGroups() {
      return drs.getNGroups();
    }

    public float getAvgBits() {
      float len = getDataLength();
      int n = getNDataPoints();
      return len * 8 / n;
    }

    public int getDrsHash() {
      return drs.hashCode();
    }

    public int getBitMap() {
      return gr.getBitmapSection().getBitMapIndicator();
    }

    public boolean getBitMapReplaced() {
      return gr.isBmsReplaced();
    }

    public final String getFile() {
      int fno = gr.getFile();
      return fileList.get(fno).getName();
    }

    public final int getTime() {
      return pds.getForecastTime();
    }

    public String getLevel() {
      int v1 = pds.getLevelType1();
      int v2 = pds.getLevelType2();
      if (v1 == 255) return "";
      if (v2 == 255) return "" + pds.getLevelValue1();
      if (v1 != v2) return pds.getLevelValue1() + "-" + pds.getLevelValue2() + " level2 type= " + v2;
      return pds.getLevelValue1() + "-" + pds.getLevelValue2();
    }

    public void toRawPdsString(Formatter f) {
      byte[] bytes = gr.getPDSsection().getRawBytes();
      int count = 1;
      for (byte b : bytes) {
        short s = DataType.unsignedByteToShort(b);
        f.format(" %d : %d%n", count++, s);
      }
    }

    public String showProcessedGridRecord(Formatter f) {
      f.format("%nFile=%s (%d)%n", fileList.get(gr.getFile()).getPath(), gr.getFile());
      GribTables.Parameter param = cust.getParameter(gr.getDiscipline(), gr.getPDS().getParameterCategory(), gr.getPDS().getParameterNumber());
      f.format("  Parameter=%s (%s)%n", param.getName(), param.getAbbrev());

      VertCoord.VertUnit levelUnit = Grib2Utils.getLevelUnit(pds.getLevelType1());
      f.format("  Level=%f/%f %s; level name =  (%s)%n", pds.getLevelValue1(), pds.getLevelValue1(), levelUnit.getUnits(), cust.getLevelNameShort(pds.getLevelType1()));

      String intvName = "none";
      if (pds instanceof Grib2Pds.PdsInterval) {
        Grib2Pds.PdsInterval pdsi = (Grib2Pds.PdsInterval) pds;
        Grib2Pds.TimeInterval[] ti = pdsi.getTimeIntervals();
        int statType = ti[0].statProcessType;
        intvName = cust.getStatisticNameShort(statType);
      }

      f.format("  Time Unit=%s ;Stat=%s%n", Grib2Utils.getCalendarPeriod( pds.getTimeUnit()), intvName);
      f.format("  ReferenceDate=%s%n", gr.getReferenceDate());
      f.format("  ForecastDate=%s%n", cust.getForecastDate(gr));
      TimeCoord.TinvDate intv = cust.getForecastTimeInterval(gr);
      if (intv != null) f.format("  TimeInterval=%s%n", intv);
      f.format("%n");
      pds.show(f);

      //CFSR malarky
      if (pds.getTemplateNumber() == 8 && cust instanceof NcepLocalTables) {
        NcepLocalTables ncepCust =  (NcepLocalTables) cust;
        ncepCust.showCfsr(pds, f);
      }

      return f.toString();
    }

    float[] readData() throws IOException {
      ucar.unidata.io.RandomAccessFile raf = getRaf();
      try {
        raf.order(ucar.unidata.io.RandomAccessFile.BIG_ENDIAN);
        return gr.readData(raf);
      } finally {
        if (raf != null)
          raf.close();
      }
    }

    ucar.unidata.io.RandomAccessFile getRaf() throws IOException {
      int fileno = gr.getFile();
      MFile mfile = fileList.get(fileno);
      return new ucar.unidata.io.RandomAccessFile(mfile.getPath(), "r");
    }


  }

  public class Gds2Bean implements Comparable<Gds2Bean> {
    Grib2SectionGridDefinition gdss;
    Grib2Gds gds;

    // no-arg constructor

    public Gds2Bean() {
    }

    public Gds2Bean(Grib2SectionGridDefinition m) {
      this.gdss = m;
      gds = gdss.getGDS();
    }

    public int getGDShash() {
      return gds.hashCode();
    }

    public int getTemplate() {
      return gdss.getGDSTemplateNumber();
    }

    public String getGridName() {
      return cust.getTableValue("3.1", gdss.getGDSTemplateNumber());
    }

    public String getGroupName() {
      return getGridName() + "-" + getNy() + "X" + getNx();
    }

    public int getNPoints() {
      return gdss.getNumberPoints();
    }

    public int getNx() {
      return gds.getNx();
    }

    public int getNy() {
      return gds.getNy();
    }

    public String getScanMode() {
      return Long.toBinaryString(gds.scanMode);
    }

    @Override
    public String toString() {
      return getGridName() + " " + getTemplate() + " " + getNx() + " X " + getNy();
    }

    public void toRawGdsString(Formatter f) {
      byte[] bytes = gds.getRawBytes();
      int count = 1;
      for (byte b : bytes) {
        short s = DataType.unsignedByteToShort(b);
        f.format(" %d : %d%n", count++, s);
      }
    }

    @Override
    public int compareTo(Gds2Bean o) {
      return getGroupName().compareTo(o.getGroupName());
    }
  }
}
