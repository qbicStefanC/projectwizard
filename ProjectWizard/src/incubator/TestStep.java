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
package incubator;

import io.DBVocabularies;

import java.util.List;

import main.ProjectwizardUI;
import model.TestSampleInformation;

import org.vaadin.teemu.wizards.WizardStep;

import uicomponents.MSPanel;
import uicomponents.TechnologiesPanel;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Wizard Step to put in information about the Sample Preparation that leads to a list of Test
 * Samples
 * 
 * @author Andreas Friedrich
 * 
 */
public class TestStep implements WizardStep {

  private VerticalLayout main;
  private TechnologiesPanel techPanel;
  // private CustomVisibilityComponent msPanelComponent;
  private MSPanel msPanel;
  private CheckBox noMeasure;
  DBVocabularies vocabs;
  private boolean containsProteins = false;

  /**
   * Create a new Sample Preparation step for the wizard
   * 
   * @param sampleTypes Available list of sample types, e.g. Proteins, RNA etc.
   */
  public TestStep(DBVocabularies vocabs) {
    this.vocabs = vocabs;
    main = new VerticalLayout();
    main.setMargin(true);
    main.setSpacing(true);
    main.setSizeUndefined();
    Label header = new Label("Analysis Method");
    main.addComponent(ProjectwizardUI
        .questionize(
            header,
            "Here you can specify what kind of material is extracted from the samples for measurement, how many measurements (techn. replicates) are taken per sample "
                + "and if there is pooling for some or all of the technologies used.",
            "Analysis Method"));
    noMeasure = new CheckBox("No further preparation of samples?");
    main.addComponent(ProjectwizardUI.questionize(noMeasure,
        "Check if no DNA etc. is extracted and measured, for example in tissue imaging.",
        "No Sample Preparation"));

    noMeasure.addValueChangeListener(new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 2393762547426343668L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        changeTechPanel();
      }
    });
  }

  @Override
  public String getCaption() {
    return "Analysis Method";
  }

  public CheckBox getNotMeasured() {
    return noMeasure;
  }

  public void changeTechPanel() {
    if (noMeasure.getValue())
      techPanel.resetInputs();
    techPanel.setEnabled(!noMeasure.getValue());
  }

  @Override
  public Component getContent() {
    return main;
  }

  @Override
  public boolean onAdvance() {
    if (techPanel.isValid() || noMeasure.getValue()) {
      if (containsProteins && msPanel.isValid())
        return true;
      else
        return false;
    } else {
      Notification n =
          new Notification("Please input at least one analyte and the number of replicates.");
      n.setStyleName(ValoTheme.NOTIFICATION_CLOSABLE);
      n.setDelayMsec(-1);
      n.show(UI.getCurrent().getPage());
      return false;
    }
  }

  @Override
  public boolean onBack() {
    return true;
  }

  public List<String> getMSEnzymes() {
    return msPanel.getEnzymes();
  }

  public List<TestSampleInformation> getSampleTypes() {
    return techPanel.getTechInfo();
  }

  public boolean hasProteins() {
    return containsProteins;
  }

  public boolean hasPools() {
    for (TestSampleInformation info : techPanel.getTechInfo())
      if (info.isPooled())
        return true;
    return false;
  }

  public void initTestStep(ValueChangeListener testPoolListener) {
    ValueChangeListener proteinListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -2885684670741817840L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        containsProteins = false;
        for (TestSampleInformation i : getSampleTypes()) {
          containsProteins |= i.getTechnology().equals("PROTEINS");
        }
        // msPanelComponent.setVisible(containsProteins);
        msPanel.setVisible(containsProteins);
      }
    };
    techPanel =
        new TechnologiesPanel(vocabs.getMeasureTypes(), new OptionGroup(""), testPoolListener,
            proteinListener);
    main.addComponent(techPanel);
    msPanel = new MSPanel(vocabs, new OptionGroup(""));
    // msPanelComponent =
    // new CustomVisibilityComponent(new MSPanel(vocabs.getEnzymes(),
    // new OptionGroup("")));
    // msPanelComponent.setVisible(false);
    Component enzymeComponent =
        ProjectwizardUI
            .questionize(
                msPanel,
                "Here you can add up to four enzymes or chemicals used to produce peptides in your MS experiment.",
                "Digestion Proteases");
    main.addComponent(enzymeComponent);
  }
}