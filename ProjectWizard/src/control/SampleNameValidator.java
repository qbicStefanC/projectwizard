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
package control;

import main.IOpenBisClient;

import com.vaadin.data.Validator;

public class SampleNameValidator implements Validator {
  
  IOpenBisClient openbis;
  
  public SampleNameValidator(IOpenBisClient openbis) {
    this.openbis = openbis;
  }
  
  @Override
  public void validate(Object value) throws InvalidValueException {
    String val = (String) value;
    if (val != null && !val.isEmpty())
      if (openbis.getProjectByCode(val.toUpperCase()) != null)
        throw new InvalidValueException("Project code already in use");
  }
}
