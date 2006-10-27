/*
* Dateiname: FunctionSelection.java
* Projekt  : WollMux
* Funktion : Speichert eine Funktionsauswahl/-def/-konfiguration des Benutzers
* 
* Copyright: Landeshauptstadt M�nchen
*
* �nderungshistorie:
* Datum      | Wer | �nderungsgrund
* -------------------------------------------------------------------
* 25.09.2006 | BNK | Erstellung
* -------------------------------------------------------------------
*
* @author Matthias Benkmann (D-III-ITD 5.1)
* @version 1.0
* 
*/
package de.muenchen.allg.itd51.wollmux.former.function;

import java.util.HashMap;
import java.util.Map;

import de.muenchen.allg.itd51.parser.ConfigThingy;

public class FunctionSelection implements FunctionSelectionAccess
{
  /**
   * Leere Liste von Parameternamen.
   */
  private static final String[] NO_PARAM_NAMES = new String[]{};
  
  /**
   * Falls der Benutzer im Experten-Modus eine Funktionsdefinition selbst geschrieben hat,
   * so wird diese hier abgelegt. Das ConfigThingy hat immer einen Wurzelknoten der keine
   * Basisfunktion ist, d.h. "AUTOFILL", "PLAUSI" oder �hnliches.
   */
  private ConfigThingy expertConf = new ConfigThingy("EXPERT");
  
  /**
   * Der Name der vom Benutzer ausgew�hlten Funktion.
   */
  private String functionName = NO_FUNCTION;
  
  /**
   * Die Namen aller Parameter, die die Funktion erwartet.
   */
  private String[] paramNames = NO_PARAM_NAMES;
  
  /**
   * Mapped die Namen der Funktionsparameter auf die vom Benutzer konfigurierten Werte
   * als {@link ParamValue} Objekte. Achtung! Diese Map enth�lt alle jemals vom Benutzer
   * gesetzten Werte, nicht nur die f�r die aktuelle Funktion. Auf diese Weise kann der
   * Benutzer die Funktion wechseln, ohne dadurch seine Eingaben von fr�her zu verlieren.
   * Bei Funktionen mit Parametern des selben Namens kann dies je nachdem ob der Name bei
   * beiden Funktionen f�r das selbe steht oder nicht zu erw�nschter
   * Erleichterung oder zu unerw�nschter Verwirrung f�hren.
   */
  private Map mapNameToParamValue = new HashMap();
  
  /**
   * Erzeugt eine FunctionSelection f�r "keine Funktion".
   * 
   * @author Matthias Benkmann (D-III-ITD 5.1)
   */
  public FunctionSelection() {}
  
  /**
   * Copy Constructor.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED */
  public FunctionSelection(FunctionSelection orig)
  {
    expertConf = new ConfigThingy(orig.expertConf);
    functionName = orig.functionName;
    this.paramNames = new String[orig.paramNames.length];
    System.arraycopy(orig.paramNames, 0, this.paramNames, 0, orig.paramNames.length);
    this.mapNameToParamValue = new HashMap(orig.mapNameToParamValue);
  }
  
  
  /* (non-Javadoc)
   * @see de.muenchen.allg.itd51.wollmux.former.FunctionSelectionAccess#isReference()
   * TESTED
   */
  public boolean isReference()
  {
    return (functionName != NO_FUNCTION && functionName != EXPERT_FUNCTION);
  }
  
  public boolean isExpert()
  {
    return functionName == EXPERT_FUNCTION;
  }
  
  public boolean isNone()
  {
    return functionName == NO_FUNCTION;
  }
  
  /* (non-Javadoc)
   * @see de.muenchen.allg.itd51.wollmux.former.FunctionSelectionAccess#getName()
   */
  public String getFunctionName() { return functionName;}
  
  public String[] getParameterNames()
  {
    return paramNames;
  }
  
  /* (non-Javadoc)
   * @see de.muenchen.allg.itd51.wollmux.former.FunctionSelectionAccess#setParameterValues(java.util.Map)
   */
  public void setParameterValues(Map mapNameToParamValue)
  {
    this.mapNameToParamValue = mapNameToParamValue;
  }
  
  /* (non-Javadoc)
   * @see de.muenchen.allg.itd51.wollmux.former.FunctionSelectionAccess#setFunction(java.lang.String, java.lang.String[])
   * TESTED
   */
  public void setFunction(String functionName, String[] paramNames)
  {
    if (functionName.equals(EXPERT_FUNCTION))
    {
      this.functionName = EXPERT_FUNCTION;
      this.paramNames = NO_PARAM_NAMES;
    }
    else if (functionName.equals(NO_FUNCTION))
    {
      this.functionName = NO_FUNCTION;
      this.paramNames = NO_PARAM_NAMES;
    }
    else
    {
      this.paramNames = new String[paramNames.length];
      System.arraycopy(paramNames, 0, this.paramNames, 0, paramNames.length);
      this.functionName = functionName;
    }
  }
  
  public ConfigThingy getExpertFunction()
  {
    return new ConfigThingy(expertConf);
  }
  
  /* (non-Javadoc)
   * @see de.muenchen.allg.itd51.wollmux.former.FunctionSelectionAccess#setExpertFunction(de.muenchen.allg.itd51.parser.ConfigThingy)
   * TESTED
   */
  public void setExpertFunction(ConfigThingy funConf)
  {
    this.functionName = EXPERT_FUNCTION;
    this.paramNames = NO_PARAM_NAMES;
    this.expertConf = new ConfigThingy(funConf);
  }
  
  /**
   * Liefert ein ConfigThingy, das diese FunctionSelection repr�sentiert (ein leeres, falls
   * keine Funktion ausgew�hlt).
   * @param root der Name des Wurzelknotens des zu liefernden ConfigThingys.
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED 
   */
  public ConfigThingy export(String root)
  {
    return export(root, null);
  }
  
  /**
   * Liefert ein ConfigThingy, das diese FunctionSelection repr�sentiert (ein leeres, falls
   * keine Funktion ausgew�hlt).
   * @param root der Name des Wurzelknotens des zu liefernden ConfigThingys.
   * @param defaultBind falls nicht null, so werden alle unspezifizierten Parameter dieser
   *        FunctionSelection an VALUE("<defaultBind>") gebunden. 
   * @author Matthias Benkmann (D-III-ITD 5.1)
   * TESTED 
   */
  public ConfigThingy export(String root, String defaultBind)
  {
    ConfigThingy rootConf = new ConfigThingy(root); 
    
    if (isReference())
    {
      ConfigThingy conf = rootConf.add("BIND");
      conf.add("FUNCTION").add(getFunctionName());
      String[] params = getParameterNames();
      for (int i = 0; i < params.length; ++i)
      {
        ParamValue value = (ParamValue)mapNameToParamValue.get(params[i]);
        if (value != null && !value.isUnspecified())
        {
          ConfigThingy set = conf.add("SET");
          set.add(params[i]);
          if (value.isFieldReference())
          {
            set.add("VALUE").add(value.getString());
          } else
          {
            set.add(value.getString());
          }
        } else if (defaultBind != null)
        {
          ConfigThingy set = conf.add("SET");
          set.add(params[i]);
          set.add("VALUE").add(defaultBind);
        }
      }
    }
    else if (isExpert())
    {
      rootConf = getExpertFunction();
      rootConf.setName(root);
    }
    
    return rootConf;
  }

  public boolean hasSpecifiedParameters()
  {
    for (int i = 0; i < paramNames.length; ++i)
    {
      ParamValue value = (ParamValue)mapNameToParamValue.get(paramNames[i]);
      if (value != null && !value.isUnspecified()) return true;
    }
    return false;
  }

  public ParamValue getParameterValue(String paramName)
  {
    ParamValue val = (ParamValue)mapNameToParamValue.get(paramName);
    if (val == null) return ParamValue.unspecified();
    return new ParamValue(val);
  }

  public void setParameterValue(String paramName, ParamValue paramValue)
  {
    mapNameToParamValue.put(paramName, paramValue);
  }

}