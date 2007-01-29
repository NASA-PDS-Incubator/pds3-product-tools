//Copyright (c) 2005, California Institute of Technology.
//ALL RIGHTS RESERVED. U.S. Government sponsorship acknowledged.
//
// $Id$ 
//

package gov.nasa.pds.tools.dict.parser;


/**
 * This exception will be thrown when the type of definition can not be determined.
 * 
 * @author pramirez
 * @version $Revision$
 * 
 */
public class UnknownDefinitionException extends Exception {
    private static final long serialVersionUID = 3768577117762341629L;

    public UnknownDefinitionException(String message) {
        super(message);
    }

}
