/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.misc;

/* $Id: RBNode.java,v 1.4 2004-01-28 21:17:13 dmaze Exp $ */

public class RBNode
{
    Object nodeData;
    RBNode left = null, right = null, parent = null;
    boolean black = false;
    
    RBNode (Object data)
    {
        assert data != null;
        nodeData = data;
    }
    
    public Object getData ()
    {
        return nodeData;
    }
    
    boolean isRed ()
    {
        return !black;
    }

    boolean isBlack ()
    {
        return black;
    }
}
