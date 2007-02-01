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

import gov.nasa.pds.tools.dict.Dictionary;
import gov.nasa.pds.tools.dict.ElementDefinition;
import gov.nasa.pds.tools.dict.ObjectDefinition;
import gov.nasa.pds.tools.dict.type.UnsupportedTypeException;
import gov.nasa.pds.tools.label.AttributeStatement;
import gov.nasa.pds.tools.label.ObjectStatement;
import gov.nasa.pds.tools.logging.ToolsLogRecord;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author pramirez
 * @version $Revision$
 * 
 */
public class ObjectValidator {
    private static Logger log = Logger.getLogger(ObjectValidator.class.getName());
    
    public static boolean isValid(Dictionary dictionary, ObjectStatement object) throws 
       DefinitionNotFoundException {
        boolean valid = true;
        
        //Find definition then validate
        ObjectDefinition definition = dictionary.findObjectClassDefinition(object.getIdentifier());
        if (definition == null)
            throw new DefinitionNotFoundException("Could not find object definition for " +
                    object.getIdentifier());
        
        //First check that required elements are captured in object
        for (Iterator i = definition.getRequiredElements().iterator(); i.hasNext();) {
            String required = (String) i.next();
            //First check to see if attribute is found by its identifier
            if (!object.hasAttribute(required)) {
                boolean foundAlias = false;
                //Next check to see if the attribute is present as an alias
                //Lookup definition for required element
                ElementDefinition elementDefinition = dictionary.getElementDefinition(required);
                //Now loop through aliases to see if the element appears
                for (Iterator a = elementDefinition.getAliases().iterator(); a.hasNext() && !foundAlias;) {
                    //All element aliases take the form <object_identifier>.<element_identifier>
                    String [] identifier = a.next().toString().split("\\.");
                    if (identifier[0].equals(definition.getIdentifier()) && object.hasAttribute(identifier[1]))
                        foundAlias = true;
                }
                //Didn't find anything time to log
                if (!foundAlias) {
                    valid = false;
                    log.log(new ToolsLogRecord(Level.SEVERE, "Object " + object.getIdentifier() + 
                            " does not contain required element " + required, object.getFilename(), 
                            object.getContext(), object.getLineNumber()));
                }
            }
        }
        
        //Run through and validate all attributes
        List attributes = object.getAttributes();
        Collections.sort(attributes);
        for (Iterator i = object.getAttributes().iterator(); i.hasNext();) {
            AttributeStatement attribute = (AttributeStatement) i.next();
            //Check to make sure element is allowed within this definition
            if (!definition.isElementPossible(attribute.getIdentifier())) {
                //Next check to see if the attribute is allowed as an alias
                //Lookup definition for element by its alias
                ElementDefinition elementDefinition = dictionary.getElementDefinition(attribute.getIdentifier());
                if (elementDefinition == null || !definition.isElementPossible(elementDefinition.getIdentifier())) {
                    valid = false;
                    log.log(new ToolsLogRecord(Level.SEVERE, "Object " + object.getIdentifier() +  " contains the element " +
                            attribute.getIdentifier() + " which is neither required nor optional.", attribute.getFilename(),
                            attribute.getContext(), attribute.getLineNumber()));
                }
            }
            //Validate attribute
            boolean elementValid = false;
            try {
                elementValid = ElementValidator.isValid(dictionary, definition.getIdentifier(), attribute);
            } catch (UnsupportedTypeException ute) {
                log.log(new ToolsLogRecord(Level.SEVERE, ute.getMessage(), attribute.getFilename(), 
                        attribute.getContext(), attribute.getLineNumber()));
            } catch (DefinitionNotFoundException dnfe) {
                log.log(new ToolsLogRecord(Level.SEVERE, dnfe.getMessage(), attribute.getFilename(), 
                        attribute.getContext(), attribute.getLineNumber()));
            }
            if (!elementValid)
                valid = false;
        }
        
        //Check to make sure that all required objects are present
        for (Iterator i = definition.getRequiredObjects().iterator(); i.hasNext();) {
            String required = (String) i.next();
            //First see if object is present
            if (!object.hasObject(required)) {
                boolean foundAlias = false;
                //Next check to see if the object is present as an alias
                //Lookup definition for required object
                ObjectDefinition objectDefinition = dictionary.getObjectDefinition(required);
                //Now loop through aliases to see if the object appears
                for (Iterator a = objectDefinition.getAliases().iterator(); a.hasNext() && !foundAlias;) {
                    String alias = a.next().toString();
                    if (object.hasObject(alias))
                        foundAlias = true;
                }
                //Didn't find anything time to log
                if (!foundAlias) {
                    valid = false;
                    log.log(new ToolsLogRecord(Level.SEVERE, "Object " + object.getIdentifier() + 
                            " does not contain required object " + required, object.getFilename(), 
                            object.getContext(), object.getLineNumber()));
                }
            }
        }
        
        //Run through nested objects and check them
        List objects = object.getObjects();
        Collections.sort(objects);
        for (Iterator i = objects.iterator(); i.hasNext();) {
            ObjectStatement obj = (ObjectStatement) i.next();
            //Check to make sure object is allowed within this definition
            if (!definition.isObjectPossible(obj.getIdentifier())) {
                //Next check to see if the object is allowed as an alias
                //Lookup definition object by its alias
                ObjectDefinition objectDefinition = dictionary.getObjectDefinition(obj.getIdentifier());
                if (objectDefinition == null || !definition.isObjectPossible(objectDefinition.getIdentifier())) {
                    valid = false;
                    log.log(new ToolsLogRecord(Level.SEVERE, "Object " + object.getIdentifier() +  " contains the element " +
                            obj.getIdentifier() + " which is neither required nor optional.", obj.getFilename(), 
                            obj.getContext(), obj.getLineNumber()));
                }
            }
            //Validate nested object
            boolean objValid = false;
            try {
                ObjectValidator.isValid(dictionary, obj);
            } catch (DefinitionNotFoundException dnfe) {
                log.log(new ToolsLogRecord(Level.SEVERE, dnfe.getMessage(), obj.getFilename(), 
                        obj.getContext(), obj.getLineNumber()));
            }
            if (!objValid)
                valid = false;
        }
        
        return valid;
    }
    
}
