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
package control;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.vaadin.ui.Notification;
import com.vaadin.ui.themes.ValoTheme;

import logging.Log4j2Logger;
import logging.Logger;

import com.vaadin.server.FontAwesome;
import com.vaadin.server.Page;
import com.vaadin.server.StreamResource;
import com.vaadin.shared.Position;

/**
 * Helper functions used for sample creation
 * 
 * @author Andreas Friedrich
 * 
 */
public class Functions {

  static Logger logger = new Log4j2Logger(Functions.class);

  /**
   * natural order comparison (a1 < a2 < a10)
   * 
   * @param a
   * @param b
   * @return
   */
  public static int compareNatural(String a, String b) {
    int la = a.length();
    int lb = b.length();
    int ka = 0;
    int kb = 0;
    while (true) {
      if (ka == la)
        return kb == lb ? 0 : -1;
      if (kb == lb)
        return 1;
      if (a.charAt(ka) >= '0' && a.charAt(ka) <= '9' && b.charAt(kb) >= '0'
          && b.charAt(kb) <= '9') {
        int na = 0;
        int nb = 0;
        while (ka < la && a.charAt(ka) == '0')
          ka++;
        while (ka + na < la && a.charAt(ka + na) >= '0' && a.charAt(ka + na) <= '9')
          na++;
        while (kb < lb && b.charAt(kb) == '0')
          kb++;
        while (kb + nb < lb && b.charAt(kb + nb) >= '0' && b.charAt(kb + nb) <= '9')
          nb++;
        if (na > nb)
          return 1;
        if (nb > na)
          return -1;
        if (ka == la)
          return kb == lb ? 0 : -1;
        if (kb == lb)
          return 1;

      }
      if (a.charAt(ka) != b.charAt(kb))
        return a.charAt(ka) - b.charAt(kb);
      ka++;
      kb++;
    }
  }
  //
  // public static double compareStringsJaroWinkler(String stringA, String stringB) {
  // return StringUtils.getJaroWinklerDistance(stringA, stringB);
  // }
  //
  // public static int compareStringsLevenshtein(String stringA, String stringB) {
  // return StringUtils.getLevenshteinDistance(stringA, stringB);
  // }

  public static StreamResource getFileStream(final String content, String name, String extension) {
    StreamResource resource = new StreamResource(new StreamResource.StreamSource() {
      @Override
      public InputStream getStream() {
        try {
          InputStream is = new ByteArrayInputStream(content.getBytes());
          return is;
        } catch (Exception e) {
          e.printStackTrace();
          return null;
        }
      }
    }, name + "." + extension);
    return resource;
  }

  /**
   * Checks if a String can be parsed to an Integer
   * 
   * @param s a String
   * @return true, if the String can be parsed to an Integer successfully, false otherwise
   */
  public static boolean isInteger(String s) {
    try {
      Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return false;
    }
    return true;
  }

  public static long getTimer() {
    return System.currentTimeMillis();
  }

  public static int compareSampleCodes(String c1, String c2) {
    if (!c1.startsWith("Q") || c1.contains("ENTITY") || !c2.startsWith("Q")
        || c2.contains("ENTITY"))
      return c1.compareTo(c2);
    try {
      // compares sample codes by projects, ending letters (999A --> 001B) and numbers (001A -->
      // 002A)
      int projCompare = c1.substring(0, 5).compareTo(c2.substring(0, 5));
      int numCompare = c1.substring(5, 8).compareTo(c2.substring(5, 8));
      int letterCompare = c1.substring(8, 9).compareTo(c2.substring(8, 9));
      if (projCompare != 0)
        return projCompare;
      else {
        if (letterCompare != 0)
          return letterCompare;
        else
          return numCompare;
      }
    } catch (Exception e) {
      logger.warn("Could not split code " + c1 + " or " + c2
          + ". Falling back to primitive lexicographical comparison.");
    }
    return c1.compareTo(c2);
  }

  public static void printElapsedTime(long startTime) {
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    System.out.println(elapsedTime);
  }

  /**
   * Increments the value of an upper case char. When at "X" restarts with "A".
   * 
   * @param c the char to be incremented
   * @return the next letter in the alphabet relative to the input char
   */
  public static char incrementUppercase(char c) {
    if (c == 'X')
      return 'A';
    else {
      int charValue = c;
      return (char) (charValue + 1);
    }
  }

  /**
   * Creates a string with leading zeroes from a number
   * 
   * @param id number
   * @param length of the final string
   * @return the completed String with leading zeroes
   */
  public static String createCountString(int id, int length) {
    String res = Integer.toString(id);
    while (res.length() < length) {
      res = "0" + res;
    }
    return res;
  }

  /**
   * Increments to the next sample string in the order, meaning the project code stays the same and
   * the 3 letter number is incremented, except if it's 999, then the following letter is
   * incremented and the number starts with 001 again.
   * 
   * @param code a 10 digit sample code
   * @return a new sample code
   */
  public static String incrementSampleCode(String code) {
    String old = code.substring(5, 8);
    String num = "";
    int newNum = Integer.parseInt(old) + 1;
    char letter = code.charAt(8);
    if (newNum > 999) {
      num = "001" + incrementUppercase(letter);
    } else
      num = createCountString(newNum, 3) + letter;
    String res = code.substring(0, 5) + num;
    return res + checksum(res);
  }

  /**
   * Checks which of two Strings can be parsed to a larger Integer and returns it.
   * 
   * @param a a String
   * @param b another String
   * @return the String that represents the larger number.
   */
  public static String max(String a, String b) {
    int a1 = Integer.parseInt(a);
    int b1 = Integer.parseInt(b);
    if (Math.max(a1, b1) == a1)
      return a;
    else
      return b;
  }

  /**
   * Computes a checksum digit for a given String. This checksum is weighted position-specific,
   * meaning it will also most likely fail a check if there is a typo of the String resulting in a
   * swapping of two numbers.
   * 
   * @param s String for which a checksum should be computed.
   * @return Character representing the checksum of the input String.
   */
  public static char checksum(String s) {
    int i = 1;
    int sum = 0;
    for (int idx = 0; idx <= s.length() - 1; idx++) {
      sum += (((int) s.charAt(idx))) * i;
      i += 1;
    }
    return mapToChar(sum % 34);
  }

  /**
   * Maps an integer to a char representation. This can be used for computing the checksum.
   * 
   * @param i number to be mapped
   * @return char representing the input number
   */
  public static char mapToChar(int i) {
    i += 48;
    if (i > 57) {
      i += 7;
    }
    return (char) i;
  }

  public static float getPercentageStep(int max) {
    return new Float(1.0 / max);
  }

  /**
   * Parses a whole String list to integers and returns them in another list.
   * 
   * @param strings List of Strings
   * @return list of integer representations of the input list
   */
  public static List<Integer> strArrToInt(List<String> strings) {
    List<Integer> res = new ArrayList<Integer>();
    for (String s : strings) {
      res.add(Integer.parseInt(s));
    }
    return res;
  }

  /**
   * Returns a String denoting the range of a list of barcodes as used in QBiC
   * 
   * @param ids List of code strings
   * @return String denoting a range of the barcodes
   */
  public static String getBarcodeRange(List<String> ids) {
    String head = getProjectPrefix(ids.get(0));
    String min = ids.get(0).substring(5, 8);
    String max = min;
    for (String id : ids) {
      String num = id.substring(5, 8);
      if (num.compareTo(min) < 0)
        min = num;
      if (num.compareTo(max) > 0)
        max = num;
    }
    return head + min + "-" + max;
  }

  /**
   * Checks if a String fits the QBiC barcode pattern
   * 
   * @param code A String that may be a barcode
   * @return true if String is a QBiC barcode, false if not
   */
  public static boolean isQbicBarcode(String code) {
    String pattern = "Q[A-Z0-9]{4}[0-9]{3}[A-Z0-9]{2}";
    return code.matches(pattern);
  }

  /**
   * Returns the 4 or 5 character project prefix used for samples in openBIS.
   * 
   * @param sample sample ID starting with a standard project prefix.
   * @return Project prefix of the sample
   */
  public static String getProjectPrefix(String sample) {
    if (isInteger("" + sample.charAt(4)))
      return sample.substring(0, 4);
    else
      return sample.substring(0, 5);
  }

  public static boolean isMeasurementOfBarcode(String code, String type) {
    String prefix = type.split("_")[1];
    code = code.replaceFirst(prefix, "");
    return isQbicBarcode(code);
  }

}
