/*******************************************************************************
 * QBiC Project Wizard enables users to create hierarchical experiments including different study
 * conditions using factorial design. Copyright (C) "2016" Andreas Friedrich
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import logging.Log4j2Logger;
import main.IOpenBisClient;
import main.OpenbisCreationController;
import uicomponents.Styles;
import model.OpenbisSpaceUserRole;

import adminviews.MCCView;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.themes.ValoTheme;

import control.Functions;
import control.Functions.NotificationType;

public class AdminView extends VerticalLayout {

  /**
   * 
   */
  private static final long serialVersionUID = -1713715806593305379L;

  IOpenBisClient openbis;
  OpenbisCreationController registrator;
  String user;

  private TabSheet tabs;
  // space
  private TextField space;
  private TextArea users;
  private Button createSpace;
  // mcc patients
  private MCCView addMultiScale;

  // edit data

  // upload metainfo
  private MetadataUploadView metadataUpload;

  // logger
  logging.Logger logger = new Log4j2Logger(AdminView.class);

  public AdminView(IOpenBisClient openbis, List<String> spaces,
      OpenbisCreationController creationController, String user) {
    this.user = user;
    this.registrator = creationController;
    this.openbis = openbis;
    tabs = new TabSheet();
    tabs.setStyleName(ValoTheme.TABSHEET_FRAMED);

    VerticalLayout spaceView = new VerticalLayout();
    spaceView.setSpacing(true);
    spaceView.setMargin(true);
    space = new TextField("Space");
    space.setWidth("300px");
    space.setStyleName(Styles.fieldTheme);
    users = new TextArea("Users");
    users.setStyleName(Styles.areaTheme);
    users.setWidth("100px");
    users.setHeight("100px");
    createSpace = new Button("Create Space");
    spaceView.addComponent(space);
    spaceView.addComponent(Styles.questionize(users,
        "Users must exist in openBIS, otherwise the space cannot be created!",
        "Add Users to Space"));
    spaceView.addComponent(createSpace);
    tabs.addTab(spaceView, "Create Space");

    // METADATA
    metadataUpload = new MetadataUploadView(openbis);
    tabs.addTab(metadataUpload, "Update Metadata");

    // MULTISCALE
    addMultiScale = new MCCView(openbis, creationController, user);
    addMultiScale.setSpacing(true);
    addMultiScale.setMargin(true);

    tabs.addTab(addMultiScale, "Add Multiscale Samples");

    addComponent(tabs);

    initButtons();
  }

  private void initButtons() {
    createSpace.addClickListener(new Button.ClickListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -6870391592753359641L;

      @Override
      public void buttonClick(ClickEvent event) {
        String space = getSpace().toUpperCase();
        if (!openbis.spaceExists(space)) {
          HashMap<OpenbisSpaceUserRole, ArrayList<String>> roleInfos =
              new HashMap<OpenbisSpaceUserRole, ArrayList<String>>();
          if (getUsers().size() > 0)
            roleInfos.put(OpenbisSpaceUserRole.USER, getUsers());
          registrator.registerSpace(space, roleInfos, user);
          int timeout = 5;
          int wait = 2;
          try {
            Thread.sleep(wait * 1000);
          } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          while (timeout > 0 && !openbis.spaceExists(space)) {
            timeout -= 1;
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
          if (openbis.spaceExists(space)) {
            Functions.notification("Space created", "The space " + space + " has been created!",
                NotificationType.SUCCESS);
            resetSpaceTab();
          } else {
            Functions.notification("Problem creating space",
                "There seems to have been a problem while creating the space. Do the specified users already exist in openbis? Of not, create them.",
                NotificationType.ERROR);
          }
        }
      }
    });
  }

  protected void resetSpaceTab() {
    users.setValue("");
    space.setValue("");
  }

  public String getSpace() {
    return space.getValue();
  }

  public ArrayList<String> getUsers() {
    if (!users.getValue().trim().equals(""))
      return new ArrayList<String>(Arrays.asList(users.getValue().split("\n")));
    else
      return new ArrayList<String>();
  }

  public Button getCreateSpace() {
    return createSpace;
  }

}
