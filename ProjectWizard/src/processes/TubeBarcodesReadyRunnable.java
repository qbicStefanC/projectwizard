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
package processes;

import java.util.List;

import main.BarcodeCreator;
import model.IBarcodeBean;

import com.vaadin.server.FileDownloader;
import com.vaadin.server.FileResource;

import views.WizardBarcodeView;

/**
 * Class implementing the Runnable interface so it can trigger a response in the view after the
 * barcode creation thread finishes
 * 
 * @author Andreas Friedrich
 * 
 */
public class TubeBarcodesReadyRunnable implements Runnable {

  private WizardBarcodeView view;
  private List<IBarcodeBean> barcodeBeans;
  BarcodeCreator creator;

  public TubeBarcodesReadyRunnable(WizardBarcodeView view, BarcodeCreator creator,
      List<IBarcodeBean> barcodeBeans) {
    this.view = view;
    this.barcodeBeans = barcodeBeans;
    this.creator = creator;
  }

  private void attachDownloadToButton() {
    FileResource pdfSource = creator.zipAndDownloadBarcodes(barcodeBeans);
    FileDownloader pdfDL = new FileDownloader(pdfSource);
    pdfDL.extend(view.getDownloadButton());
  }

  @Override
  public void run() {
    attachDownloadToButton();
    view.creationDone();
    view.tubesReady();
  }
}
