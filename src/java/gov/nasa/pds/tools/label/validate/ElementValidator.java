// Copyright 2006, by the California Institute of 
// Technology. ALL RIGHTS RESERVED. United States Government 
// Sponsorship acknowledged. Any commercial use must be negotiated with 
// the Office of Technology Transfer at the California Institute of 
// Technology.
//
// This software may be subject to U.S. export control laws. By 
// accepting this software, the user agrees to comply with all 
// applicable U.S. export laws and regulations. User has the 
// responsibility to obtain export licenses, or other export authority 
// as may be required before exporting such information to foreign 
// countries or providing access to foreign persons.
//
// $Id$ 
//

package gov.nasa.pds.tools.label.validate;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.nasa.pds.tools.dict.Dictionary;
import gov.nasa.pds.tools.dict.ElementDefinition;
import gov.nasa.pds.tools.label.AttributeStatement;
import gov.nasa.pds.tools.label.Numeric;
import gov.nasa.pds.tools.label.Sequence;
import gov.nasa.pds.tools.label.Set;
import gov.nasa.pds.tools.label.Value;
import gov.nasa.pds.tools.logging.ToolsLogRecord;
import gov.nasa.pds.tools.util.Utility;
import gov.nasa.pds.tools.dict.type.InvalidLengthException;
import gov.nasa.pds.tools.dict.type.NumericTypeChecker;
import gov.nasa.pds.tools.dict.type.OutOfRangeException;
import gov.nasa.pds.tools.dict.type.TypeChecker;
import gov.nasa.pds.tools.dict.type.TypeCheckerFactory;
import gov.nasa.pds.tools.dict.type.UnsupportedTypeException;
import gov.nasa.pds.tools.dict.type.InvalidTypeException;
import gov.nasa.pds.tools.dict.DictionaryTokens;

/**
 * This class will validate an element value or set of values against 
 * an ElementDefinition.
 * @author pramirez
 * @version $Revision$
 * 
 */
public class ElementValidator implements DictionaryTokens {
    private static Logger log = Logger.getLogger(ElementValidator.class.getName());
    
    public static boolean isValid(ElementDefinition definition, AttributeStatement attribute) 
            throws UnsupportedTypeException {
        boolean valid = true;
        Value value = attribute.getValue();
        
        //Check length of namespace
        if (attribute.hasNamespace()) {
            if (attribute.getNamespace().length() > NAMESPACE_LENGTH) {
                valid = false;
                log.log(new ToolsLogRecord(Level.SEVERE, "Namespace exceeds max length of " + 
                    ELEMENT_IDENT_LENGTH + " characters.", attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
            }
        }
        
        //Check length of identifier
        if (attribute.getElementIdentifier().length() > ELEMENT_IDENT_LENGTH) {
            valid = false;
            log.log(new ToolsLogRecord(Level.SEVERE, "Identifier exceeds max length of " + 
                    ELEMENT_IDENT_LENGTH + " characters.", attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
        }

        //Load the type checker
        TypeChecker checker = TypeCheckerFactory.getInstance().newInstance(definition.getDataType());
        //Validate the value
        boolean valueValid = validate(definition, attribute, checker, value);
        //Don't want to set to true if has already been set to false
        if (!valueValid)
            valid = false;
        
        return valid;
    }
    
    private static boolean validate(ElementDefinition definition, AttributeStatement attribute, TypeChecker checker, Value value) 
            throws UnsupportedTypeException {
        boolean valid = true;
        if (value == null) {
            log.log(new ToolsLogRecord(Level.WARNING, "Found no value for " + attribute.getIdentifier(), 
                    attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
        } else if (value instanceof Set || value instanceof Sequence) {
            boolean validValues = true;
            for (Iterator i = ((Collection) value).iterator(); i.hasNext();) {
                Value v = (Value) i.next();
                validValues = validate(definition, attribute, checker, v);
                //Don't want to set to true if has already been set to false
                if (!validValues)
                    valid = false;
            }
        } else {
            if (!skipValue(value.toString())) {
                //Check against valid values if there are any
                if (definition.hasValidValues()) {
                    if (!definition.getValues().contains(value.toString())) {
                        boolean foundValue = false;
                        boolean manipulated = false;
                        
                        //Perform whitespace stripping
                        String filteredValue = Utility.stripNewLines(value.toString());
                        
                        if (definition.getValues().contains(filteredValue)) {
                            foundValue = true;
                        } else {
                            //Continue with whitespace striping
                            filteredValue = Utility.filterString(filteredValue);
                            manipulated = true;
                            if (definition.getValues().contains(filteredValue))
                                foundValue = true;
                        }
                       
                        if (!foundValue) {
                            //Could not find valid value.
                            valid = false;
                            //Only produce a warning if the standard value list is anything other than static
                            if (!VALUE_TYPE_STATIC.equals(definition.getValueType())) {
                               log.log(new ToolsLogRecord(Level.WARNING, value.toString() + 
                                        " is not in the suggested list of valid values for " + attribute.getIdentifier(), 
                                        attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                            } else {
                               log.log(new ToolsLogRecord(Level.SEVERE, value.toString() + 
                                         " is not in the list of valid values for " + attribute.getIdentifier(),
                                         attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                            }
                        } else if (manipulated) {
                            //Value had to be manipulated to make a match
                            log.log(new ToolsLogRecord(Level.INFO, "Manipulated value for " + attribute.getIdentifier() +
                                       " to find valid value.", attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                        }
                    }
                }
                
                Object castedValue = null;
                //Try to cast to an instance of the type
                try {
                    castedValue = checker.cast(value.toString());
                } catch (InvalidTypeException ite) {
                    valid = false;
                    log.log(new ToolsLogRecord(Level.SEVERE, ite.getMessage(), attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                }
                
                //Check min length
                try {
                    checker.checkMinLength(value.toString(), definition.getMinLength());
                } catch (InvalidLengthException ile) {
                    valid = false;
                    log.log(new ToolsLogRecord(Level.SEVERE, ile.getMessage(), attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                }
                
                //Check max length
                try {
                    checker.checkMaxLength(value.toString(), definition.getMaxLength());
                } catch (InvalidLengthException ile) {
                    valid = false;
                    log.log(new ToolsLogRecord(Level.SEVERE, ile.getMessage(), attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                }
                
                //Check to see if this is a numeric type checker if so then do further checking
                if (checker instanceof NumericTypeChecker && castedValue instanceof Number && castedValue != null) {
                    NumericTypeChecker numericChecker = (NumericTypeChecker) checker;
                    
                    //Check min value
                    if (definition.hasMinimum()) {
                        try {
                            numericChecker.checkMinValue((Number) castedValue, definition.getMinimum());
                        } catch (OutOfRangeException oor) {
                            valid = false;
                            log.log(new ToolsLogRecord(Level.SEVERE, oor.getMessage(), attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                        }
                    }
                    
                    //Check max value
                    if (definition.hasMaximum()) {
                        try {
                            numericChecker.checkMaxValue((Number) castedValue, definition.getMaximum());
                        } catch (OutOfRangeException oor) {
                            valid = false;
                            log.log(new ToolsLogRecord(Level.SEVERE, oor.getMessage(), attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                        }
                    }
                    
                    //Check units if found and definition required.
                    //Look at definition to see if we should check units
                    if (value instanceof Numeric && !skipValue(definition.getUnitId())) {
                        Numeric number = (Numeric) value;
                        if ("NONE".equals(definition.getUnitId()) && number.getUnits() != null) {
                            log.log(new ToolsLogRecord(Level.WARNING, "Units specified for " + attribute.getElementIdentifier() +
                                    " when none should be present. Found " + number.getUnits(), attribute.getFilename(), 
                                    attribute.getContext(), attribute.getLineNumber()));
                        } else if (number.getUnits() != null && !definition.isUnitAllowed(number.getUnits().toUpperCase())) {
                            log.log(new ToolsLogRecord(Level.SEVERE, "Units do not match those specified for " +  
                                    attribute.getElementIdentifier() +" by dictionary. Found " + number.getUnits(),
                                    attribute.getFilename(), attribute.getContext(), attribute.getLineNumber()));
                        }
                    }
                }
            }
        }
        
        return valid;
    }
    
    private static boolean skipValue(String value) {
        if ("N/A".equals(value) || "NULL".equals(value) || "UNK".equals(value))
            return true;
        return false;
    }
    
    /**
     * Checks to see whether an {@link AttributeStatement} is correct. Will look up the definition in
     * the given dictionary. An object context may be supplied as elements can have aliases that are 
     * appropriate within an object. Set objectContext to null if there if the lookup should be performed
     * without care to the surrounding object.
     * @param dictionary where to look up the element
     * @param objectContext enclosing the element to be looked up
     * @param attribute statement to be validated
     * @return flag indicting whether or not the statement was valid against the definition found
     * @throws DefinitionNotFoundException if definition for element is not found
     * @throws UnsupportedTypeException if type of element is not supported
     */
    public static boolean isValid(Dictionary dictionary, String objectContext, AttributeStatement attribute) 
            throws DefinitionNotFoundException, UnsupportedTypeException {
        ElementDefinition definition = dictionary.getElementDefinition(objectContext, attribute.getIdentifier()); 
        
        if (definition == null)
            throw new DefinitionNotFoundException("Undefined Element: " + attribute.getIdentifier());
   
        return isValid(definition, attribute);
    }
    
    public static boolean isValid(Dictionary dictionary, AttributeStatement attribute) 
            throws DefinitionNotFoundException, UnsupportedTypeException {
        return isValid(dictionary, null, attribute);
    }

}
