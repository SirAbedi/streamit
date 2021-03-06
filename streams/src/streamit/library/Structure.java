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

package streamit.library;

import streamit.scheduler2.Scheduler;

public abstract class Structure extends Stream implements Cloneable
{
    // Can't add child streams to a structure.
    public void add(Stream s) { throw new UnsupportedOperationException(); }
    public void connectGraph() { }
    public void setupBufferLengths(Scheduler schedule) { }
    public Object clone() throws CloneNotSupportedException { return super.clone(); }
}
