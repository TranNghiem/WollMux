/*
* Dateiname: MailMerge.java
* Projekt  : WollMux
* Funktion : Druckfunktionen f�r den Seriendruck.
* 
* Copyright: Landeshauptstadt M�nchen
*
* �nderungshistorie:
* Datum      | Wer | �nderungsgrund
* -------------------------------------------------------------------
* 05.01.2007 | BNK | Erstellung
* 15.01.2007 | BNK | Fortschrittsindikator
* -------------------------------------------------------------------
*
* @author Matthias Benkmann (D-III-ITD 5.1)
* @version 1.0
* 
*/
package de.muenchen.allg.itd51.wollmux.func;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XNameAccess;
import com.sun.star.sdb.CommandType;
import com.sun.star.sdbc.XConnection;
import com.sun.star.sdbc.XDataSource;
import com.sun.star.sheet.XCellRangesQuery;
import com.sun.star.sheet.XSheetCellRanges;
import com.sun.star.sheet.XSpreadsheetDocument;
import com.sun.star.table.CellRangeAddress;
import com.sun.star.table.XCellRange;
import com.sun.star.text.XTextDocument;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.parser.ConfigThingy;
import de.muenchen.allg.itd51.wollmux.Logger;
import de.muenchen.allg.itd51.wollmux.TextDocumentModel;
import de.muenchen.allg.itd51.wollmux.TimeoutException;
import de.muenchen.allg.itd51.wollmux.XPrintModel;
import de.muenchen.allg.itd51.wollmux.db.ColumnNotFoundException;
import de.muenchen.allg.itd51.wollmux.db.Dataset;
import de.muenchen.allg.itd51.wollmux.db.Datasource;
import de.muenchen.allg.itd51.wollmux.db.OOoDatasource;
import de.muenchen.allg.itd51.wollmux.db.QueryResults;

public class MailMerge
{
  /**
   * Anzahl Millisekunden, die maximal gewartet wird, bis alle Datens�tze f�r den
   * Serienbrief aus der Datenbank gelesen wurden.
   */
  private static final int DATABASE_TIMEOUT = 20000;
  
  /**
   * Druckt das zu pmod geh�rende Dokument f�r alle Datens�tze (offerSelection==true)
   * oder die Datens�tze, die der Benutzer in einem Dialog
   * ausw�hlt (offerSelection == false) aus der aktuell �ber
   * Bearbeiten/Datenbank austauschen eingestellten Tabelle. 
   * F�r die Anzeige der Datens�tze im Dialog wird die Spalte "WollMuxDescription"
   * verwendet. Falls die Spalte "WollMuxSelected" vorhanden ist und "1", "ja" oder "true"
   * enth�lt, so ist der entsprechende Datensatz in der Auswahlliste bereits vorselektiert.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED
   */
  public static void mailMerge(XPrintModel pmod, boolean offerSelection)
  { //TESTED
    XTextDocument doc = pmod.getTextDocument();
    XPropertySet settings = null;
    try{
      settings = UNO.XPropertySet(UNO.XMultiServiceFactory(doc).createInstance("com.sun.star.document.Settings"));
    } catch(Exception x)
    {
      Logger.error("Kann DocumentSettings nicht auslesen", x);
      return;
    }
    
    String datasource = (String)UNO.getProperty(settings, "CurrentDatabaseDataSource");
    String table = (String)UNO.getProperty(settings, "CurrentDatabaseCommand");
    Integer type = (Integer) UNO.getProperty(settings, "CurrentDatabaseCommandType"); 
    
    Logger.debug("Ausgew�hlte Datenquelle: \""+datasource+"\"  Tabelle/Kommando: \""+table+"\"  Typ: \""+type+"\"");
    
    mailMerge(pmod, datasource, table, type, offerSelection);
  }

  /**
   * Falls offerSelection == false wird das zu pmod geh�rende Dokument f�r jeden Datensatz aus 
   * Tabelle table in Datenquelle datasource einmal ausgedruckt.
   * Falls offerSelection == true, wird dem Benutzer ein Dialog pr�sentiert, in dem er die
   * "WollMuxDescription"-Spalten aller Datens�tze angezeigt bekommt und die auszudruckenden 
   * Datens�tze ausw�hlen kann. Dabei sind alle Datens�tze, die eine Spalte "WollMuxSelected"
   * haben, die den Wert "true", "ja" oder "1" enth�lt bereits vorselektiert.  
   * @param type muss {@link CommandType#TABLE} sein.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED
   */
  private static void mailMerge(XPrintModel pmod, String datasource, String table, Integer type, boolean offerSelection)
  {
    /*
     * Kann nur mit Tabellennamen umgehen, nicht mit beliebigen Statements. Falls eine andere
     * Art von Kommando eingestellt ist, wird der SuperMailMerge gestartet, damit der
     * Benutzer eine Tabelle ausw�hlt.
     */
    if (datasource == null || datasource.length() == 0 || table == null || table.length() == 0 || type == null || type.intValue() != CommandType.TABLE)
    {
      superMailMerge(pmod);
      return;
    }

    ConfigThingy conf = new ConfigThingy("Datenquelle");
    conf.add("NAME").add("Knuddel");
    conf.add("TABLE").add(table);
    conf.add("SOURCE").add(datasource);
    Datasource ds;
    try{
      ds = new OOoDatasource(new HashMap(),conf,new URL("file:///"));
    }catch(Exception x)
    {
      Logger.error(x);
      return;
    }
    
    Set schema = ds.getSchema();
    QueryResults data;
    try
    {
      data = ds.getContents(DATABASE_TIMEOUT);
    }
    catch (TimeoutException e)
    {
      Logger.error("Konnte Daten f�r Serienbrief nicht aus der Datenquelle auslesen",e);
      return;
    }
    
    mailMerge(pmod, offerSelection, schema, data);
  }

  /**
   * Falls offerSelection == false wird das zu pmod geh�rende Dokument f�r jeden Datensatz aus 
   * data einmal ausgedruckt.
   * Falls offerSelection == true, wird dem Benutzer ein Dialog pr�sentiert, in dem er die
   * "WollMuxDescription"-Spalten aller Datens�tze angezeigt bekommt und die auszudruckenden 
   * Datens�tze ausw�hlen kann. Dabei sind alle Datens�tze, die eine Spalte "WollMuxSelected"
   * haben, die den Wert "true", "ja" oder "1" enth�lt bereits vorselektiert.  
   * @param schema muss die Namen aller Spalten f�r den MailMerge enthalten.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED
   */
  private static void mailMerge(XPrintModel pmod, boolean offerSelection, Set schema, QueryResults data)
  {
    Vector list = new Vector();
    Iterator iter = data.iterator();
    while (iter.hasNext())
    {
      Dataset dataset = (Dataset)iter.next();
      list.add(new ListElement(dataset));
    }
    
    if (offerSelection) 
    {
      if (!selectFromListDialog(list)) return;
    }

    boolean modified = pmod.getDocumentModified(); //Modified-Zustand merken, um ihn nachher wiederherzustellen
    pmod.collectNonWollMuxFormFields(); //falls der Benutzer manuell welche hinzugefuegt hat
    
    MailMergeProgressWindow progress = new MailMergeProgressWindow(list.size());
    
    iter = list.iterator();
    while (iter.hasNext())
    {
      progress.makeProgress();
      ListElement ele = (ListElement)iter.next();
      if (offerSelection && !ele.isSelected()) continue;
      Iterator colIter = schema.iterator();
      while (colIter.hasNext())
      {
        String column = (String)colIter.next();
        String value = null;
        try
        {
          value = ele.getDataset().get(column);
        }
        catch (Exception e)
        {
          Logger.error("Spalte \""+column+"\" fehlt unerkl�rlicherweise => Abbruch des Drucks",e);
          return;
        }
        
        if (value != null) pmod.setFormValue(column, value);
      }
      pmod.print((short)1);
    }
    
    progress.close();
    
    pmod.setDocumentModified(modified);
  }
  
  private static class MailMergeProgressWindow
  {
    private JFrame myFrame;
    private JLabel countLabel;
    private int count = 0;
    private int maxcount;
    
    MailMergeProgressWindow(final int maxcount)
    {
      this.maxcount = maxcount;
      try{
        SwingUtilities.invokeAndWait(new Runnable(){
          public void run()
          {
            myFrame = new JFrame("Seriendruck");
            Box vbox = Box.createVerticalBox();
            myFrame.getContentPane().add(vbox);
            Box hbox = Box.createHorizontalBox();
            vbox.add(hbox);
            hbox.add(Box.createHorizontalStrut(5));
            hbox.add(new JLabel("Verarbeite Dokument"));
            hbox.add(Box.createHorizontalStrut(5));
            countLabel = new JLabel("   -");
            hbox.add(countLabel);
            hbox.add(new JLabel(" / "+maxcount+"    "));
            hbox.add(Box.createHorizontalStrut(5));
            myFrame.setAlwaysOnTop(true);
            myFrame.pack();
            int frameWidth = myFrame.getWidth();
            int frameHeight = myFrame.getHeight();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = screenSize.width/2 - frameWidth/2; 
            int y = screenSize.height/2 - frameHeight/2;
            myFrame.setLocation(x,y);
            myFrame.setVisible(true);
          }});
      }catch(Exception x){Logger.error(x);};
    }

    public void makeProgress()
    {
      try{
        SwingUtilities.invokeLater(new Runnable(){
          public void run()
          {
            ++count;
            countLabel.setText(""+count);
            if (maxcount > 0) myFrame.setTitle(""+Math.round(100*(double)count/maxcount)+"%");
          }});
      }catch(Exception x){Logger.error(x);};
    }
    
    public void close()
    {
      try{
        SwingUtilities.invokeLater(new Runnable(){
          public void run()
          {
            myFrame.dispose();
          }});
      }catch(Exception x){Logger.error(x);};
    }
  }
  
  /**
   * Wrapper f�r ein Dataset, um es einerseits in eine JList packen zu k�nnen, andererseits auch
   * daf�r, den Zustand ausgew�hlt oder nicht speichern zu k�nnen.
   *
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  private static class ListElement
  {
    private Dataset ds;
    private boolean selected = false;
    private String description = "Keine Beschreibung vorhanden";
    
    /**
     * Initialisiert dieses ListElement mit dem Dataset ds, wobei falls vorhanden die Spalten
     * "WollMuxDescription" und "WollMuxSelected" ausgewertet werden, um den toString()
     * respektive isSelected() Wert zu bestimmen.
     * @author Matthias Benkmann (D-III-ITD 5.1)
     */
    public ListElement(Dataset ds)
    {
      this.ds = ds;
      try{
        String des = ds.get("WollMuxDescription");
        if (des != null && des.length() > 0) description = des;
      } catch(Exception x){}
      try{
        String sel = ds.get("WollMuxSelected");
        if (sel != null && (sel.equalsIgnoreCase("true") || sel.equals("1") || sel.equalsIgnoreCase("ja"))) selected = true;
      } catch(Exception x){}
    }
    public void setSelected(boolean selected)
    {
      this.selected = selected;
    }
    public boolean isSelected() { return selected; }
    public Dataset getDataset() {return ds;}
    public String toString() { return description;}
  }
  
  /**
   * Pr�sentiert einen Dialog, der den Benutzer aus list (enth�lt {@link ListElement}s) ausw�hlen
   * l�sst. ACHTUNG! Diese Methode kehrt erst zur�ck nachdem der Benutzer den Dialog geschlossen
   * hat.
   * @return true, gdw der Benutzer mit Okay best�tigt hat.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED
   */
  private static boolean selectFromListDialog(final Vector list)
  {
    final boolean[] result = new boolean[]{false,false}; 
    //  GUI im Event-Dispatching Thread erzeugen wg. Thread-Safety.
    try{
      javax.swing.SwingUtilities.invokeLater(new Runnable() {
        public void run() {
            try{createSelectFromListDialog(list, result);}catch(Exception x)
            {
              Logger.error(x);
              synchronized(result)
              {
                result[0] = true;
                result.notifyAll();
              }
            };
        }
      });
      
      synchronized(result)
      {
        while(!result[0]) result.wait();
      }
      return result[1];
      
    }
    catch(Exception x) 
    {
      Logger.error(x);
      return false;
    }
  }
  
  /**
   * Pr�sentiert einen Dialog, der den Benutzer aus list (enth�lt {@link ListElement}s) ausw�hlen
   * l�sst. ACHTUNG! Diese Methode darf nur im Event Dispatching Thread aufgerufen werden. 
   * @param result ein 2-elementiges Array auf das nur synchronisiert zugegriffen wird. Das
   *        erste Element wird auf false gesetzt, sobald der Dialog geschlossen wird.
   *        Das zweite Element wird in diesem Fall auf true gesetzt, wenn der Benutzer mir Okay
   *        best�tigt hat. Bei sonstigen Arten, den Dialog zu beenden bleibt das zweite Element
   *        unangetastet, sollte also mit false vorbelegt werden.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED
   */
  private static void createSelectFromListDialog(final Vector list, final boolean[] result)
  {
    final JFrame myFrame = new JFrame("Gew�nschte Ausdrucke w�hlen");
    myFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    myFrame.addWindowListener(new WindowListener(){
      public void windowOpened(WindowEvent e) {}
      public void windowClosing(WindowEvent e) {}
      public void windowClosed(WindowEvent e) 
      {
        synchronized(result)
        {
          result[0] = true;
          result.notifyAll();
        }
      }
      public void windowIconified(WindowEvent e) {}
      public void windowDeiconified(WindowEvent e) {}
      public void windowActivated(WindowEvent e) { }
      public void windowDeactivated(WindowEvent e) {}});
    myFrame.setAlwaysOnTop(true);
    JPanel myPanel = new JPanel(new BorderLayout());
    myPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
    myFrame.setContentPane(myPanel);
    
    final JList myList = new JList(list);
    myList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    for (int i = 0; i < list.size(); ++i)
    {
      ListElement ele = (ListElement)list.get(i);
      if (ele.isSelected()) myList.addSelectionInterval(i,i);
    }
    
    JScrollPane scrollPane = new JScrollPane(myList);
    myPanel.add(scrollPane, BorderLayout.CENTER);
    
    Box top = Box.createVerticalBox();
    top.add(new JLabel("Bitte w�hlen Sie, welche Ausdrucke Sie bekommen m�chten"));
    top.add(Box.createVerticalStrut(5));
    myPanel.add(top, BorderLayout.NORTH);
    
    Box bottomV = Box.createVerticalBox();
    bottomV.add(Box.createVerticalStrut(5));
    Box bottom = Box.createHorizontalBox();
    bottomV.add(bottom);
    myPanel.add(bottomV, BorderLayout.SOUTH);
    
    JButton button = new JButton("Abbrechen");
    button.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e)
      {
        myFrame.dispose();
      }}
    );
    bottom.add(button);
    
    bottom.add(Box.createHorizontalGlue());
    
    button = new JButton("Alle");
    button.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e)
      {
        myList.setSelectionInterval(0, list.size()-1);
      }}
    );
    bottom.add(button);
    
    bottom.add(Box.createHorizontalStrut(5));
    
    button = new JButton("Keinen");
    button.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e)
      {
        myList.clearSelection();
      }}
    );
    bottom.add(button);
    
    bottom.add(Box.createHorizontalGlue());
    
    button = new JButton("Drucken");
    button.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e)
      {
        for (int i = 0; i < list.size(); ++i) ((ListElement)list.get(i)).setSelected(false);
        int[] sel = myList.getSelectedIndices();
        for (int i = 0; i < sel.length; ++i)
        {
          ((ListElement)list.get(sel[i])).setSelected(true);
        }
        synchronized(result)
        {
          result[1] = true;
        }
        myFrame.dispose();
      }}
    );
    bottom.add(button);
    
    myFrame.pack();
    int frameWidth = myFrame.getWidth();
    int frameHeight = myFrame.getHeight();
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    int x = screenSize.width/2 - frameWidth/2; 
    int y = screenSize.height/2 - frameHeight/2;
    myFrame.setLocation(x,y);
    myFrame.setVisible(true);
    myFrame.requestFocus();
  }

  private static class CalcCellQueryResults implements QueryResults
  {
    /**
     * Bildet einen Spaltennamen auf den Index in dem zu dem Datensatz geh�renden
     * String[]-Array ab.
     */
    private Map mapColumnNameToIndex;
   
    private List datasets = new ArrayList();
    
    public int size()
    {
      return datasets.size();
    }

    public Iterator iterator()
    {
      return datasets.iterator();
    }

    public boolean isEmpty()
    {
      return datasets.isEmpty();
    }

    public void setColumnNameToIndexMap(Map mapColumnNameToIndex)
    {
      this.mapColumnNameToIndex = mapColumnNameToIndex;
    }

    public void addDataset(String[] data)
    {
      datasets.add(new MyDataset(data));
    }
    
    private class MyDataset implements Dataset
    {
      private String[] data;
      public MyDataset(String[] data)
      {
        this.data = data;
      }

      public String get(String columnName) throws ColumnNotFoundException
      {
        Number idx = (Number)mapColumnNameToIndex.get(columnName);
        if (idx == null) throw new ColumnNotFoundException("Spalte "+columnName+" existiert nicht!");
        return data[idx.intValue()];
      }

      public String getKey()
      {
        return "key";
      }
      
    }
    
  }
  
  /**
   * Liefert die sichtbaren Zellen des Arbeitsblattes mit Namen sheetName aus dem Calc Dokument,
   * dessen Fenstertitel windowTitle ist. Die erste Zeile der Calc-Tabelle wird herangezogen
   * als Spaltennamen. Diese Spaltennamen werden zu schema hinzugef�gt.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED
   */
  private static QueryResults getVisibleCalcData(String windowTitle, String sheetName, Set schema)
  {
    CalcCellQueryResults results = new CalcCellQueryResults();
    
    try{
      XSpreadsheetDocument doc = null;
      XEnumeration xenu = UNO.desktop.getComponents().createEnumeration();
      while(xenu.hasMoreElements())
      {
        doc = UNO.XSpreadsheetDocument(xenu.nextElement());
        if (doc != null)
        {
          String title = (String)UNO.getProperty(UNO.XModel(doc).getCurrentController().getFrame(),"Title");
          if (windowTitle.equals(title)) break;
        }
      }
      
      if (doc != null)
      {
        XCellRangesQuery sheet = UNO.XCellRangesQuery(doc.getSheets().getByName(sheetName));
        if (sheet != null)
        {
          SortedSet columnIndexes = new TreeSet();
          SortedSet rowIndexes = new TreeSet();
          XSheetCellRanges visibleCellRanges = sheet.queryVisibleCells();
          XSheetCellRanges nonEmptyCellRanges = sheet
              .queryContentCells((short) ( com.sun.star.sheet.CellFlags.VALUE
                                         | com.sun.star.sheet.CellFlags.DATETIME
                                         | com.sun.star.sheet.CellFlags.STRING 
                                         | com.sun.star.sheet.CellFlags.FORMULA));
          CellRangeAddress[] nonEmptyCellRangeAddresses = nonEmptyCellRanges.getRangeAddresses();
          for (int i = 0; i < nonEmptyCellRangeAddresses.length; ++i)
          {
            XSheetCellRanges ranges = UNO.XCellRangesQuery(visibleCellRanges).queryIntersection(nonEmptyCellRangeAddresses[i]);
            CellRangeAddress[] rangeAddresses = ranges.getRangeAddresses();
            for (int k = 0; k < rangeAddresses.length; ++k)
            {
              CellRangeAddress addr = rangeAddresses[k];
              for (int x = addr.StartColumn; x <= addr.EndColumn; ++x)
                columnIndexes.add(new Integer(x));
              
              for (int y = addr.StartRow; y <= addr.EndRow; ++y)
                rowIndexes.add(new Integer(y));
            }
          }
          
          if (columnIndexes.size() > 0 && rowIndexes.size() > 0)
          {
            XCellRange sheetCellRange = UNO.XCellRange(sheet);
            
            /*
             * Erste sichtbare Zeile durchscannen und alle nicht-leeren Zelleninhalte als
             * Tabellenspaltennamen interpretieren. Ein Mapping in
             * mapColumnNameToIndex wird erzeugt, wobei NICHT auf den Index in
             * der Calc-Tabelle gemappt wird, sondern auf den Index im sp�ter f�r jeden
             * Datensatz existierenden String[]-Array.
             */
            int ymin = ((Number)rowIndexes.first()).intValue();
            Map mapColumnNameToIndex = new HashMap();
            int idx = 0;
            Iterator iter = columnIndexes.iterator();
            while (iter.hasNext())
            {
              int x = ((Number)iter.next()).intValue();
              String columnName = UNO.XTextRange(sheetCellRange.getCellByPosition(x, ymin)).getString();
              if (columnName.length() > 0)
              {
                mapColumnNameToIndex.put(columnName, new Integer(idx));
                schema.add(columnName);
                ++idx;  
              }
              else 
                iter.remove(); //Spalten mit leerem Spaltennamen werden nicht ben�tigt.
            }
            
            results.setColumnNameToIndexMap(mapColumnNameToIndex);
            
            /*
             * Datens�tze erzeugen
             */
            Iterator rowIndexIter = rowIndexes.iterator();
            rowIndexIter.next(); //erste Zeile enth�lt die Tabellennamen, keinen Datensatz
            while (rowIndexIter.hasNext())
            {
              int y = ((Number)rowIndexIter.next()).intValue();
              String[] data = new String[columnIndexes.size()];
              Iterator columnIndexIter = columnIndexes.iterator();
              idx = 0;
              while (columnIndexIter.hasNext())
              {
                int x = ((Number)columnIndexIter.next()).intValue();
                String value = UNO.XTextRange(sheetCellRange.getCellByPosition(x, y)).getString();
                data[idx++] = value;
              }
              
              results.addDataset(data);
            }
          }
        }
      }
    }catch(Exception x)
    {
      Logger.error(x);
    }
    
    return results;
  }
  
  /**
   * Startet den ultimativen Seriendruck f�r pmod.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  public static void superMailMerge(XPrintModel pmod)
  {
    SuperMailMerge.superMailMerge(pmod);
  }
  
  /**
   * Klasse, die den ultimativen Seriendruck realisiert.
   *
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  private static class SuperMailMerge
  {
    /**
     * Liste von {@link Runnable}-Objekten, die sequentiell abgearbeitet werden im
     * Nicht-Event-Dispatching-Thread.
     */
    private List todo = new LinkedList();
    
    /**
     * Wird dies auf false gesetzt, so beendet sich {@link #run()}.
     */
    private boolean running = true;
    
    /**
     * Die Menge der Namen aller OOo-Datenquellen.
     */
    private Set datasourceNames = new TreeSet();
    
    /**
     * Die Menge aller Titel von offenen Calc-Dokument-Fenstern.
     */
    private Set calcDocumentTitles = new TreeSet();
    
    /**
     * Die ComboBox in der der Benutzer die OOo-Datenquelle bzw, das Calc-Dokument f�r
     * den MailMerge ausw�hlen kann.
     */
    private JComboBox datasourceSelector;
    
    /**
     * Das XPrintModel f�r diesen MailMerge.
     */
    private XPrintModel pmod;
    
    /**
     * Die ComboBox in der der Benutzer die Tabelle f�r den MailMerge ausw�hlen kann.
     */
    private JComboBox tableSelector;
    
    /**
     * Der Name der aktuell ausgew�hlten Datenquelle (bzw, der Titel des ausgew�hlten
     * Calc-Dokuments). ACHTUNG! Diese Variable wird initial vom
     * Nicht-EDT bef�llt, dann aber nur noch im Event Dispatching Thread verwendet bis zu dem
     * Zeitpunkt wo die Datenquellenauswahl beendet ist und der Druck durch den nicht-EDT
     * Thread angeleiert wird.
     */
    private String selectedDatasource = "";
    
    /**
     * Der Name der aktuell ausgew�hlten Tabelle. ACHTUNG! Diese Variable wird initial vom
     * Nicht-EDT bef�llt, dann aber nur noch im Event Dispatching Thread verwendet bis zu dem
     * Zeitpunkt wo die Datenquellenauswahl beendet ist und der Druck durch den nicht-EDT
     * Thread angeleiert wird.
     */
    private String selectedTable = "";
    
    /**
     * Startet den ultimativen MailMerge. ACHTUNG! Diese Methode kehrt erst zur�ck, wenn der
     * Ausdruck abgeschlossen oder abgebrochen wurde.
     * @author Matthias Benkmann (D-III-ITD 5.1)
     */
    public static void superMailMerge(XPrintModel pmod)
    {
      SuperMailMerge merge = new SuperMailMerge(pmod);
      merge.run();
    }
    
    private SuperMailMerge(XPrintModel pmod)
    { //TESTED
      this.pmod = pmod;
      
      /*
       * Namen aller OOo-Datenquellen bestimmen.
       */
      String[] datasourceNamesA = UNO.XNameAccess(UNO.dbContext).getElementNames();
      for (int i = 0; i < datasourceNamesA.length; ++i)
        datasourceNames.add(datasourceNamesA[i]);
      
      /*
       * Titel aller offenen Calc-Fenster bestimmen.
       */
      XEnumeration xenu = UNO.desktop.getComponents().createEnumeration();
      while(xenu.hasMoreElements())
      {
        try{
          XSpreadsheetDocument doc = UNO.XSpreadsheetDocument(xenu.nextElement());
          if (doc != null)
          {
            String title = (String)UNO.getProperty(UNO.XModel(doc).getCurrentController().getFrame(),"Title");
            if (title != null)
              calcDocumentTitles.add(title);
          }
        }catch(Exception x)
        {
          Logger.error(x);
        }
      }
      
      /*
       * Aktuell �ber Bearbeiten/Datenbank austauschen gew�hlte Datenquelle/Tabelle bestimmen,
       * falls gesetzt.
       */
      XTextDocument doc = pmod.getTextDocument();
      XPropertySet settings = null;
      try{
        settings = UNO.XPropertySet(UNO.XMultiServiceFactory(doc).createInstance("com.sun.star.document.Settings"));
      } catch(Exception x)
      {
        Logger.error("Kann DocumentSettings nicht auslesen", x);
        return;
      }
      String datasource = (String)UNO.getProperty(settings, "CurrentDatabaseDataSource");
      String table = (String)UNO.getProperty(settings, "CurrentDatabaseCommand");
      Integer type = (Integer) UNO.getProperty(settings, "CurrentDatabaseCommandType");
      if (datasource != null && datasourceNames.contains(datasource) && table != null && 
          table.length() > 0 && type != null && type.intValue() == CommandType.TABLE)
      {
        selectedDatasource = datasource;
        selectedTable = table;
      }
      
      /*
       * Erzeugen der GUI auf die todo-Liste setzen.
       */
      todo.add(new Runnable(){
        public void run(){   
          inEDT("createGUI");
        }});
    }

    /**
     * Arbeitet die {@link #todo}-Liste ab, solange {@link #running}==true.
     * 
     * @author Matthias Benkmann (D-III-ITD 5.1)
     * TESTED
     */
    private void run()
    {
      try
      {
        while(running)
        {
          Runnable r;
          synchronized(todo)
          {
            while(todo.isEmpty()) todo.wait();
            r = (Runnable)todo.remove(0);
          }
          r.run();
        }
      }
      catch (Exception e)
      {
        Logger.error(e);
      }
    }
    
    /**
     * Erstellt die GUI f�r die Auswahl der Datenquelle/Tabelle f�r den SuperMailMerge.
     * Darf nur im EDT aufgerufen werden.
     * 
     * @author Matthias Benkmann (D-III-ITD 5.1)
     * TESTED
     */
    public void createGUI()
    {
      final JFrame myFrame = new JFrame("Seriendruck");
      myFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      myFrame.addWindowListener(new WindowListener(){
        public void windowOpened(WindowEvent e) {}
        public void windowClosing(WindowEvent e) 
        {
          stopRunning();
          myFrame.dispose();
        }
        public void windowClosed(WindowEvent e){}
        public void windowIconified(WindowEvent e) {}
        public void windowDeiconified(WindowEvent e) {}
        public void windowActivated(WindowEvent e) { }
        public void windowDeactivated(WindowEvent e) {}}
      );
      
      myFrame.setAlwaysOnTop(true);
      Box vbox = Box.createVerticalBox();
      JPanel myPanel = new JPanel();
      //myPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
      myFrame.add(myPanel);
      myPanel.add(vbox);
      
      /*
       * Datenquellen-Auswahl-ComboBox bauen
       */
      Box hbox = Box.createHorizontalBox();
      vbox.add(hbox);
      hbox.add(new JLabel("Datenquelle"));
      datasourceSelector = new JComboBox();
      hbox.add(Box.createHorizontalStrut(5));
      hbox.add(datasourceSelector);
      int selected = 0;
      int idx = 0;
      Iterator iter = calcDocumentTitles.iterator();
      while (iter.hasNext())
      {
        datasourceSelector.addItem(iter.next());
        ++idx;
      }
      iter = datasourceNames.iterator();
      while (iter.hasNext())
      {
        String dsName = (String)iter.next();
        if (dsName.equals(selectedDatasource)) selected = idx;
        datasourceSelector.addItem(dsName);
        ++idx;
      }
      
      if (idx > 0) 
      {
        datasourceSelector.setSelectedIndex(selected);
        String newDatasource = (String)datasourceSelector.getSelectedItem();
        if (newDatasource != null) selectedDatasource = newDatasource;
      }
      
      /*
       * Auf �nderungen der Datenquellen-Auswahl-Combobox reagieren.
       */
      datasourceSelector.addItemListener(new ItemListener(){
        public void itemStateChanged(ItemEvent e)
        {
          String newDatasource = (String)datasourceSelector.getSelectedItem();
          String newTable = (String)tableSelector.getSelectedItem();
          if (newDatasource != null && !newDatasource.equals(selectedDatasource))
          {
            selectedDatasource = newDatasource;
            selectedTable = newTable;
            addTodo("updateTableSelector", new String[]{selectedDatasource, selectedTable});
          }
        }});
      
      /*
       * Tabellenauswahl-ComboBox bauen.
       */
      hbox = Box.createHorizontalBox();
      vbox.add(Box.createVerticalStrut(5));
      vbox.add(hbox);
      hbox.add(new JLabel("Tabelle"));
      hbox.add(Box.createHorizontalStrut(5));
      tableSelector = new JComboBox();
      hbox.add(tableSelector);
      
      /*
       * Buttons hinzuf�gen.
       */
      
      hbox = Box.createHorizontalBox();
      vbox.add(Box.createVerticalStrut(5));
      vbox.add(hbox);
      JButton button = new JButton("Abbrechen");
      button.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e)
        {
          stopRunning();
          myFrame.dispose();
        }});
      hbox.add(button);
      
      button = new JButton("Alle Drucken");
      button.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e)
        {
          selectedTable = (String)tableSelector.getSelectedItem();
          selectedDatasource = (String)datasourceSelector.getSelectedItem();
          if (selectedTable != null && selectedDatasource != null) 
          {
            clearTodo();
            addTodo("print", new Boolean(false));
            myFrame.dispose();
          }
        }});
      hbox.add(Box.createHorizontalStrut(5));
      hbox.add(button);
      
      button = new JButton("Einzelauswahl");
      button.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e)
        {
          selectedTable = (String)tableSelector.getSelectedItem();
          selectedDatasource = (String)datasourceSelector.getSelectedItem();
          if (selectedTable != null && selectedDatasource != null) 
          {
            clearTodo();
            addTodo("print", new Boolean(true));
            myFrame.dispose();
          }
        }});
      hbox.add(Box.createHorizontalStrut(5));
      hbox.add(button);
      
      addTodo("updateTableSelector", new String[]{selectedDatasource, selectedTable});
      
      myFrame.pack();
      int frameWidth = myFrame.getWidth();
      int frameHeight = myFrame.getHeight();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      int x = screenSize.width/2 - frameWidth/2; 
      int y = screenSize.height/2 - frameHeight/2;
      myFrame.setLocation(x,y);
      myFrame.setVisible(true);
      myFrame.requestFocus();
    }
    
    /**
     * Wird im Nicht-EDT aufgerufen und bestimmt die Tabellen der neu ausgew�hlten
     * Datenquelle und l�sst dann im EDT die {@link #tableSelector}-ComboBox updaten.
     * @param datasourceAndTableName das erste Element ist der Name der neu ausgew�hlten
     *        Datenquelle bzw. des Calc-Dokuments. Das zweite Element ist der Name der
     *        vorher ausgew�hlten Tabelle (oder null). Letzterer wird ben�tigt, da falls die neue
     *        Datenquelle eine Tabelle gleichen Namens besitzt, diese als aktuelle
     *        Auswahl der ComboBox eingestellt werden soll.
     * @author Matthias Benkmann (D-III-ITD 5.1)
     * TESTED
     */
    public void updateTableSelector(String[] datasourceAndTableName)
    {
      String datasourceName = datasourceAndTableName[0];
      final String tableName = datasourceAndTableName[1]; //ACHTUNG!! Darf null sein!
      String[] tableNames = null;
      if (calcDocumentTitles.contains(datasourceName))
      {
        XEnumeration xenu = UNO.desktop.getComponents().createEnumeration();
        while(xenu.hasMoreElements())
        {
          try{
            XSpreadsheetDocument doc = UNO.XSpreadsheetDocument(xenu.nextElement());
            if (doc != null)
            {
              String title = (String)UNO.getProperty(UNO.XModel(doc).getCurrentController().getFrame(),"Title");
              if (datasourceName.equals(title))
              {
                tableNames = UNO.XNameAccess(doc.getSheets()).getElementNames();
                break;
              }
            }
          }catch(Exception x)
          {
            Logger.error(x);
            return;
          }
        }
      }
      else if (datasourceNames.contains(datasourceName))
      {
        try{
          XDataSource ds = UNO.XDataSource(UNO.dbContext.getRegisteredObject(datasourceName));
          ds.setLoginTimeout(DATABASE_TIMEOUT);
          XConnection conn = ds.getConnection("","");
          XNameAccess tables = UNO.XTablesSupplier(conn).getTables();
          tableNames = tables.getElementNames();
        } catch(Exception x)
        {
          Logger.error(x);
          return;
        }
      } else return; //kann passieren, falls weder Datenquellen noch Calc-Dokumente vorhanden.
      
      if (tableNames == null || tableNames.length == 0) tableNames = new String[]{"n/a"};
      
      final String[] tNames = tableNames; 
      try{
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            tableSelector.removeAllItems();
            int selected = 0;
            for (int i = 0; i < tNames.length; ++i)
            {
              if (tNames[i].equals(tableName))
                selected = i;
              tableSelector.addItem(tNames[i]);
            }
            tableSelector.setSelectedIndex(selected);
          }
        });
      }
      catch(Exception x) 
      {
        Logger.error(x);
      }
    }
    
    public void print(Boolean offerselection)
    {
      if (calcDocumentTitles.contains(selectedDatasource))
      {
        Set schema = new HashSet();
        QueryResults data = getVisibleCalcData(selectedDatasource, selectedTable, schema);
        mailMerge(pmod, offerselection.booleanValue(), schema, data);
      }
      else
        mailMerge(pmod, selectedDatasource, selectedTable, new Integer(CommandType.TABLE), offerselection.booleanValue());
    }
    
    /**
     * F�gt den Aufruf der public-Methode method zur {@link #todo}-Liste hinzu.
     * @param method der Name einer public-Methode.
     * @param param Parameter, der der Methode �bergeben werden soll, oder null falls
     * die Methode keine Parameter erwartet.
     * @author Matthias Benkmann (D-III-ITD 5.1)
     * TESTED
     */
    private void addTodo(String method, Object param)
    {
      try{
        Class[] paramTypes = null;
        Object[] params = null;
        if (param != null)
        {
          paramTypes = new Class[]{param.getClass()};
          params = new Object[]{param};
        }
        final Object[] finalParams = params;
        final Method m = this.getClass().getMethod(method,paramTypes);
        final SuperMailMerge self = this;
        synchronized(todo)
        {
          todo.add(new Runnable() {
            public void run() {
              try{
                m.invoke(self, finalParams);
              }catch(Exception x)
              {
                Logger.error(x);
              };
            }
          });
          todo.notifyAll();
        }
      }
      catch(Exception x) 
      {
        Logger.error(x);
      }
    }
    
    /**
     * Leert die {@link #todo}-Liste.
     * 
     * @author Matthias Benkmann (D-III-ITD 5.1)
     */
    private void clearTodo()
    {
      synchronized(todo)
      {
        todo.clear();
      }
    }
    
    /**
     * L�scht die {@link #todo}-Liste und f�gt ihr dann einen Befehl zum Setzen 
     * von {@link #running} auf false hinzu.
     * 
     * @author Matthias Benkmann (D-III-ITD 5.1)
     */
    private void stopRunning()
    {
      synchronized(todo)
      {
        todo.clear();
        todo.add(new Runnable(){
          public void run()
          {
            running = false;
          }});
        todo.notifyAll();
      }
    }
    
    /**
     * F�hrt die public-Methode "method" im EDT aus (ansynchron).
     * @param method der Name einer public-Methode
     * @author Matthias Benkmann (D-III-ITD 5.1)
     * TESTED
     */
    private void inEDT(String method)
    {
      try{
        final Method m = this.getClass().getMethod(method,null);
        final SuperMailMerge self = this;
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
          public void run() {
              try{
                m.invoke(self, null);
              }catch(Exception x)
              {
                Logger.error(x);
              };
          }
        });
      }
      catch(Exception x) 
      {
        Logger.error(x);
      }
    }
    
  }

  
  
  public static void main(String[] args) throws Exception
  {
     UNO.init();
     
     XTextDocument doc = UNO.XTextDocument(UNO.desktop.getCurrentComponent());
     if (doc == null) 
     {
       System.err.println("Vordergrunddokument ist kein XTextDocument!");
       System.exit(1);
     }
     
     XPrintModel pmod = new TextDocumentModel(doc).getPrintModel();
     superMailMerge(pmod);
     
     System.exit(0);
  }

}