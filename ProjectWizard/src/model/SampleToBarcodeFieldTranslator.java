/*******************************************************************************
 * QBiC Project Wizard enables users to create hierarchical experiments including different study conditions using factorial design.
 * Copyright (C) "2016"  Andreas Friedrich
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package model;

import java.util.Map;

import logging.Log4j2Logger;

import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Sample;

import com.vaadin.ui.ComboBox;

import control.BarcodeController;

public class SampleToBarcodeFieldTranslator {

  private logging.Logger logger = new Log4j2Logger(SampleToBarcodeFieldTranslator.class);

  public String buildInfo(ComboBox select, Sample s, String parents, boolean cut) {
    Map<String, String> map = s.getProperties();
    String in = "";
    if (select.getValue() != null)
      in = select.getValue().toString();
    String res = "";
    switch (in) {
      case "Tissue/Extr. Material":
        if (map.containsKey("Q_PRIMARY_TISSUE"))
          res = map.get("Q_PRIMARY_TISSUE");
        else
          res = map.get("Q_SAMPLE_TYPE");
        break;
      case "Secondary Name":
        res = map.get("Q_SECONDARY_NAME");
        break;
      case "Parent Samples (Source)":
        if (parents == null) {
          logger
              .error("Calls from BarcodePreviewComponent should not be able to select Parent samples as Info field choice."
                  + "Setting from null to empty String to continue.");
          parents = "";
        }
        res = parents;
        break;
      case "QBiC ID":
        res = s.getCode();
        break;
      case "Lab ID":
        res = map.get("Q_EXTERNALDB_ID");
        break;
      default:
        if (!in.equals(""))
          logger.error("Unknown input: " + in + ". Field will be empty!");
    }
    if (res == null)
      return "";
    if (cut)
      res = res.substring(0, Math.min(res.length(), 22));
    return res;
  }

  public String getCodeString(Sample sample, String codedName) {
    Map<String, String> map = sample.getProperties();
    String res = "";
    // @SuppressWarnings("unchecked")
    // Set<String> selection = (Set<String>) codedName.getValue();
    // for (String s : selection) {
    String s = codedName;
    if (!res.isEmpty())
      res += "_";
    switch (s) {
      case "QBiC ID":
        res += sample.getCode();
        break;
      case "Secondary Name":
        res += map.get("Q_SECONDARY_NAME");
        break;
      case "Lab ID":
        res += map.get("Q_EXTERNALDB_ID");
        break;
    }
    // }
    res = fixFileName(res);
    return res.substring(0, Math.min(res.length(), 21));
  }

  private String fixFileName(String res) {
    res = res.replace("null", "");
    res = res.replace(";", "_");
    res = res.replace("#", "_");
    res = res.replace(" ", "_");
    while (res.contains("__"))
      res = res.replace("__", "_");
    return res;
  }
}
