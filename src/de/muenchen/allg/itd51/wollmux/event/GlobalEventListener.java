/*
 * Dateiname: GlobalEventListener.java
 * Projekt  : WollMux
 * Funktion : Reagiert auf globale Ereignisse
 * 
 * Copyright (c) 2008 Landeshauptstadt München
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the European Union Public Licence (EUPL),
 * version 1.0 (or any later version).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * European Union Public Licence for more details.
 *
 * You should have received a copy of the European Union Public Licence
 * along with this program. If not, see
 * http://ec.europa.eu/idabc/en/document/7330
 *
 * Änderungshistorie:
 * Datum      | Wer | Änderungsgrund
 * -------------------------------------------------------------------
 * 14.10.2005 | LUT | Erstellung
 * 09.11.2005 | LUT | + Logfile wird jetzt erweitert (append-modus)
 *                    + verwenden des Konfigurationsparameters SENDER_SOURCE
 *                    + Erster Start des wollmux über wm_configured feststellen.
 * 05.12.2005 | BNK | line.separator statt \n     
 * 13.04.2006 | BNK | .wollmux/ Handling ausgegliedert in WollMuxFiles.
 * 20.04.2006 | LUT | Überarbeitung Code-Kommentare  
 * 20.04.2006 | BNK | DEFAULT_CONTEXT ausgegliedert nach WollMuxFiles
 * 21.04.2006 | LUT | + Robusteres Verhalten bei Fehlern während dem Einlesen 
 *                    von Konfigurationsdateien; 
 *                    + wohldefinierte Datenstrukturen
 *                    + Flag für EventProcessor: acceptEvents
 * 08.05.2006 | LUT | + isDebugMode()
 * 10.05.2006 | BNK | +parseGlobalFunctions()
 *                  | +parseFunctionDialogs()
 * 26.05.2006 | BNK | DJ initialisierung ausgelagert nacht WollMuxFiles
 * 06.06.2006 | LUT | + Ablösung der Event-Klasse durch saubere Objektstruktur
 * 19.12.2006 | BAB | + setzen von Shortcuts im Konstruktor
 * 29.12.2006 | BNK | +registerDatasources()
 * 27.03.2007 | BNK | Default-oooEinstellungen ausgelagert nach data/...
 * 17.05.2010 | BED | Workaround für Issue #100374 bei OnSave/OnSaveAs-Events
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD 5.1)
 * 
 */

package de.muenchen.allg.itd51.wollmux.event;

import com.sun.star.frame.XModel;
import com.sun.star.lang.EventObject;
import com.sun.star.lang.XComponent;
import com.sun.star.text.XTextDocument;

import de.muenchen.allg.afid.UNO;
import de.muenchen.allg.itd51.wollmux.DocumentManager;
import de.muenchen.allg.itd51.wollmux.Logger;
import de.muenchen.allg.itd51.wollmux.Workarounds;
import de.muenchen.allg.itd51.wollmux.DocumentManager.Info;

/**
 * Der GlobalEventListener sorgt dafür, dass der WollMux alle wichtigen globalen
 * Ereignisse wie z.B. ein OnNew on OnLoad abfangen und darauf reagieren kann. In
 * diesem Fall wird die Methode notifyEvent aufgerufen. Wichtig ist dabei, dass der
 * Verarbeitungsstatus für alle Dokumenttypen (auch nicht-Textdokumente) erfasst
 * wird, damit der WollMux auch für diese Komponenten onWollMuxProcessingFinished
 * liefern kann.
 * 
 * @author christoph.lutz
 */
public class GlobalEventListener implements com.sun.star.document.XEventListener
{
  private static final String ON_SAVE_AS = "OnSaveAs";

  private static final String ON_SAVE = "OnSave";

  private static final String ON_UNLOAD = "OnUnload";

  private static final String ON_CREATE = "OnCreate";

  private static final String ON_VIEW_CREATED = "OnViewCreated";

  private DocumentManager docManager;

  public GlobalEventListener(DocumentManager docManager)
  {
    this.docManager = docManager;
  }

  /**
   * NICHT SYNCHRONIZED, weil es Deadlocks gibt zwischen getUrl() und der Zustellung
   * bestimmter Events (z.B. OnSave).
   */
  public void notifyEvent(com.sun.star.document.EventObject docEvent)
  {
    // Der try-catch-Block verhindert, daß die Funktion und damit der
    // ganze Listener ohne Fehlermeldung abstürzt.
    try
    {
      // Zur Optimierung werden hier gemeinsame Code-Teile auf das Nötigste
      // reduziert. Es gibt viele Events, die den WollMux überhaupt nicht
      // interessieren, da sollte der WollMux nichts tun (auch ein UNO-Cast kann hier
      // schon unnötig Performance fressen)
      if (docEvent.Source == null) return;
      String event = docEvent.EventName;

      if (ON_CREATE.equals(event))
        onCreate(docEvent.Source);
      else if (ON_VIEW_CREATED.equals(event))
        onViewCreated(docEvent.Source);
      else if (ON_UNLOAD.equals(event))
        onUnload(docEvent.Source);
      else if (ON_SAVE.equals(event))
        onSaveOrSaveAs(docEvent.Source);
      else if (ON_SAVE_AS.equals(event)) onSaveOrSaveAs(docEvent.Source);
    }
    catch (Exception e)
    {
      Logger.error(e);
    }
  }

  /**
   * OnCreate ist das erste Event das aufgerufen wird, wenn ein neues leeres Dokument
   * über eine Factory erzeugt wird wie z.B. mit loadComponentFromURL(...
   * "private:factory/swriter" ...) oder in OOo über Datei->Neu. Auch OOo erzeugt
   * manchmal im Hintergrund unsichtbare leere Dokumente über die Factory. Bekannt
   * sind folgende Fälle: Beim OOo-Seriendruck über den Seriendruck-Assistent (für
   * jeden Datensatz); Beim Einfügen von Autotexten (z.B. mit "bt<F3>" in OOo).
   * 
   * Das Event kommt nicht, wenn ein Dokument von einer Datei geladen oder von einer
   * Vorlage erzeugt wird. Das Event kommt auch dann nicht, wenn eine Vorlagendatei
   * als Standardvorlage für neue Dokumente definiert ist und Datei->Neu verwendet
   * wird.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private void onCreate(Object source)
  {
    XComponent compo = UNO.XComponent(source);
    if (compo == null) return;

    // durch das Hinzufügen zum docManager kann im Event onViewCreated erkannt
    // werden, dass das Dokument frisch erzeugt wurde:
    XTextDocument xTextDoc = UNO.XTextDocument(source);
    if (xTextDoc != null)
      docManager.addTextDocument(xTextDoc);
    else
      docManager.add(compo);
    // Verarbeitet wird das Dokument erst bei onViewCreated
  }

  /**
   * OnViewCreated kommt, wenn ein Dokument seitens OOo vollständig aufgebaut ist.
   * Das Event kommt bei allen Dokumenten, egal ob sie neu erzeugt, geladen, sichtbar
   * oder unsichtbar sind.
   * 
   * Da das Event in allen möglichen Fällen kommt, und die Bearbeitung von
   * unsichtbaren Dokumenten durch den WollMux für eine andere stadtinterne Anwendung
   * (JavaComm) notwendig ist, wird in diesem Event die eigentliche Verarbeitung von
   * Dokumenten durch den WollMux angestoßen.
   * 
   * Ausgenommen von der Verarbeitung werden temporäre Dokumente des OOo-Seriendrucks
   * und alle gerade erzeugten, unsichtbaren Textdokumente.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private void onViewCreated(Object source)
  {
    XModel compo = UNO.XModel(source);
    if (compo == null) return;

    // Keine Aktion bei neu (mit Create) erzeugten und temporären, unsichtbaren
    // Textdokumente des OOo-Seriendrucks. Sicherstellen, dass diese Dokumente auch
    // nicht im docManager mitgeführt werden.
    if (isTempMailMergeDocument(compo))
    {
      // docManager.remove(source) ist hier nicht erforderlich, weil für Dokumente
      // mit URL kein OnCreate-Event kommt.
      return;
    }
    Info docInfo = docManager.getInfo(compo);
    // docInfo ist != null, wenn das Dokument mit Create erzeugt wurde.
    XTextDocument xTextDoc = UNO.XTextDocument(compo);
    if (xTextDoc != null && docInfo != null && isDocumentHidden(compo))
    {
      docManager.remove(compo);
      return;
    }

    // Dokument ggf. in docManager aufnehmen und abhängig vom Typ verarbeiten.
    if (xTextDoc != null)
    {
      if (docInfo == null) docManager.addTextDocument(xTextDoc);
      WollMuxEventHandler.handleProcessTextDocument(xTextDoc,
        !isDocumentHidden(compo));
    }
    else
    {
      if (docInfo == null) docManager.add(compo);
      WollMuxEventHandler.handleNotifyDocumentEventListener(null,
        WollMuxEventHandler.ON_WOLLMUX_PROCESSING_FINISHED, compo);
    }
  }

  /**
   * onSave oder onSaveAs werden beim Speichern von Dokumenten aufgerufen.
   * 
   * Wir verwenden diese beiden Events derzeit nur für einen Workaround für
   * Issue100374, so dass die Methode wieder entfernt werden kann, wenn der
   * Workaround nicht mehr benötigt wird.
   * 
   * @author Christoph Lutz (D-III-ITD-D101) TESTED
   */
  private void onSaveOrSaveAs(Object source)
  {
    // Falls die OOo-Version von Issue 100374 betroffen ist, fangen wir noch
    // die "OnSave"- und "OnSaveAs"-Events ab, um alle Notizen zu löschen und
    // wieder neu anzulegen. Ansonsten gehen beim Speichern die vom WollMux in
    // den Notizen veränderten Daten verloren.
    if (Workarounds.applyWorkaroundForOOoIssue100374())
    {
      XTextDocument xTextDoc = UNO.XTextDocument(source);
      // Alle Notizen löschen und mit selbem Inhalt neu anlegen
      DocumentManager.Info info = docManager.getInfo(xTextDoc);
      if (info != null)
      {
        info.getTextDocumentModel().rewritePersistantData();
      }
    }
  }

  /**
   * OnUnlaod kommt als letztes Event wenn ein Dokument geschlossen wurde. Wir nutzen
   * dieses Event um den docManager aufzuräumen und angeschlossene Listener zu
   * informieren.
   * 
   * @author Christoph Lutz (D-III-ITD-D101) TESTED
   */
  private void onUnload(Object source)
  {
    DocumentManager.Info info = docManager.remove(source);
    // info kann auch null sein, wenn es sich z.B. um ein temporäres Dokument des
    // Seriendrucks handelt
    /**
     * ACHTUNG! ACHTUNG! Zu folgender Zeile unbedingt {@link
     * WollMuxEventHandler#handleTextDocumentClosed(DocumentManager.Info} lesen. Hier
     * darf AUF KEINEN FALL info.hasTextDocumentModel() getestet oder
     * info.getTextDocumentModel() aufgerufen werden!
     */
    if (info != null) WollMuxEventHandler.handleTextDocumentClosed(info);
  }

  /**
   * Liefert zurück, ob es sich bei dem Dokument source um ein Temporäres Dokument
   * des OOo-Seriendrucks handelt und wird benötigt um solche Dokumente im Workaround
   * für Ticket #3091 zu ignorieren. Dabei kann diese Methode nur Dokumente erkennen,
   * die anhand der Eigenschaft URL als temporäre Datei zu erkennen sind.
   * 
   * Anmerkung: Der OOo-Seriendruck kann über Datei->Drucken und über
   * Extras->Seriendruck-Assistent gestartet werden. Verschiedene OOo-Versionen
   * verhalten sich diesbezüglich verschieden:
   * 
   * OOo 3.0.1 erzeugt in beiden Varianten für jeden Datensatz eine unsichtbare
   * temporäre Datei mit einer URL, die eine Erkennung der temporären Datei zulässt.
   * 
   * OOo 3.2.1 erzeugt nur noch über Datei->Drucken temoräre Dateien mit gesetzter
   * URL. Über Extras->Seriendruck-Assistent ist die URL-Eigenschaft jedoch nicht
   * mehr gesetzt, so dass diese Methode nicht mehr ausreicht, um temporäre Dokumente
   * des Seriendrucks zu identifizieren.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private boolean isTempMailMergeDocument(XModel compo)
  {
    String url = compo.getURL();
    Logger.debug2(url);
    int idx = url.lastIndexOf('/') - 4;
    return (url.startsWith(".tmp/sv", idx) && url.endsWith(".tmp"));
  }

  /**
   * Liefert false zurück, wenn das Dokument sichtbar ist und true, wenn das Dokument
   * unsichtbar ist oder keine View besitzt.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private boolean isDocumentHidden(XModel compo)
  {
    try
    {
      return Boolean.TRUE.equals(UNO.getProperty(
        compo.getCurrentController().getFrame(), "IsHidden"));
    }
    catch (Exception x)
    {
      // Falls der Zugriff auf den aktuellen Controller/Frame scheitert
      // (NullPointerException), dann ist das Dokument nicht sichtbar. Ein
      // null-Frame ist genauso unsichtbar wie ein Frame mit IsHidden==true.
      return true;
    }
  }

  public void disposing(EventObject arg0)
  {
  // nothing to do
  }
}
