package uicomponents;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.WordUtils;

import com.vaadin.data.Item;
import com.vaadin.ui.Table;
import com.vaadin.ui.themes.ValoTheme;

import main.SampleSummaryBean;

public class ExperimentSummaryTable extends Table {

  public ExperimentSummaryTable() {
    super();
    setCaption("Summary");
    addContainerProperty("Type", String.class, null);
    addContainerProperty("Content", String.class, null);
    addContainerProperty("Samples", Integer.class, null);
    setStyleName(ValoTheme.TABLE_SMALL);
    setPageLength(1);
  }

  public void setSamples(List<SampleSummaryBean> beans) {
    removeAllItems();
    int i = 0;
    for (SampleSummaryBean b : beans) {
      String content = WordUtils.capitalizeFully(b.getSampleContent().replace("_", " "));
      i++;
      int amount = Integer.parseInt(b.getAmount());
      String type = "Unknown";
      String sampleType = b.getSampleType();
      switch (sampleType) {
        case "Biological Source":
          type = "Sample Sources";
          break;
        case "Extracted Samples":
          type = "Sample Extracts";
          break;
        case "Prepared Samples":
          type = "Sample Preparations";
          break;
        default:
          type = sampleType;
      }
      if (b.isPartOfSplit())
        type = "Split " + type;
      if (b.isPool())
        type = "Pooled " + type;
      addItem(new Object[] {type, content, amount}, i);
    }
    setPageLength(i);
    styleTable();
  }

  private String parseCell(Object id, String propertyName) {
    Item item = getItem(id);
    return (String) item.getItemProperty(propertyName).getValue();
  }

  private void styleTable() {
    String base = "blue-hue";
    List<String> styles = new ArrayList<String>();
    for (int i = 1; i < 7; i++)
      styles.add(base + Integer.toString(i));

    // Set cell style generator
    setCellStyleGenerator(new Table.CellStyleGenerator() {

      @Override
      public String getStyle(Table source, Object itemId, Object propertyId) {
        String type = parseCell(itemId, "Type");
        if (type.contains("Sample Sources"))
          return styles.get(0);
        else if (type.contains("Sample Extracts"))
          return styles.get(1);
        else if (type.contains("Sample Preparations")) {
          String analyte = parseCell(itemId, "Content");
          if (!analyte.equals("Peptides"))
            return styles.get(2);
          else
            return styles.get(3);
        } else if (type.contains("Mass Spectrometry Run"))
          return styles.get(4);
        else
          return "";
      }

    });
  }

}
