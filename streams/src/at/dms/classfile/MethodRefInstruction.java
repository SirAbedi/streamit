/*
 * Copyright (C) 1990-2001 DMS Decision Management Systems Ges.m.b.H.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id: MethodRefInstruction.java,v 1.2 2006-01-25 17:00:39 thies Exp $
 */

package at.dms.classfile;

import java.io.DataOutput;
import java.io.IOException;

import at.dms.util.InconsistencyException;

/**
 * Instruction that references method
 * opc_invokevirtual,_invokespecial, opc_invokestatic
 */
public class MethodRefInstruction extends Instruction {

    // --------------------------------------------------------------------
    // CONSTRUCTORS
    // --------------------------------------------------------------------

    /**
     * Constructs a new method reference instruction
     *
     * @param   opcode      the opcode of the instruction
     * @param   name        the qualified name of the referenced object
     * @param   type        the signature of the referenced object
     */
    public MethodRefInstruction(int opcode, String name, String type) {
        super(opcode);

        this.method = new MethodRefConstant(name, type);
    }

    /**
     * Constructs a new method reference instruction
     *
     * @param   opcode      the opcode of the instruction
     * @param   name        the qualified name of the referenced object
     * @param   type        the signature of the referenced object
     */
    public MethodRefInstruction(int opcode, String owner, String name, String type) {
        super(opcode);

        this.method = new MethodRefConstant(owner, name, type);
    }

    /**
     * Constructs a new method reference instruction from a class file
     *
     * @param   opcode      the opcode of the instruction
     * @param   method      the method reference (as pooled constant)
     */
    public MethodRefInstruction(int opcode, MethodRefConstant method) {
        super(opcode);

        this.method = method;
    }

    // --------------------------------------------------------------------
    // ACCESSORS
    // --------------------------------------------------------------------

    /**
     * Returns true iff control flow can reach the next instruction
     * in textual order.
     */
    public boolean canComplete() {
        return true;
    }

    /**
     * Insert or check location of constant value on constant pool
     *
     * @param   cp      the constant pool for this class
     */
    /*package*/ void resolveConstants(ConstantPool cp) {
        cp.addItem(method);
    }

    /**
     * Returns the number of bytes used by the the instruction in the code array.
     */
    /*package*/ int getSize() {
        return 1 + 2;
    }

    /**
     * Returns the method reference constant used by this instruction
     */
    public MethodRefConstant getMethodRefConstant() {
        return method;
    }

    // --------------------------------------------------------------------
    // CHECK CONTROL FLOW
    // --------------------------------------------------------------------

    /**
     * Returns the type pushed on the stack
     */
    public byte getReturnType() {
        String  type = method.getType();

        switch (type.charAt(type.indexOf(")") + 1)) {
        case 'Z':
        case 'B':
        case 'C':
        case 'S':
        case 'I':
            return TYP_INT;
        case 'F':
            return TYP_FLOAT;
        case 'L':
        case '[':
            return TYP_REFERENCE;
        case 'D':
            return TYP_DOUBLE;
        case 'J':
            return TYP_LONG;

        default:
            throw new InconsistencyException("invalid signature " + type);
        }
    }

    /**
     * Returns the size of data pushed on the stack by this instruction
     */
    public int getPushedOnStack() {
        String  type = method.getType();

        switch (type.charAt(type.indexOf(")") + 1)) {
        case 'V':
            return 0;
        case 'Z':
        case 'B':
        case 'C':
        case 'S':
        case 'F':
        case 'I':
        case 'L':
        case '[':
            return 1;
        case 'D':
        case 'J':
            return 2;
        default:
            throw new InconsistencyException("invalid signature x" + type);
        }
    }

    /**
     * Return the amount of stack (positive or negative) used by this instruction
     */
    public int getStack() {
        String  type = method.getType();
        int     used = 0;

        if (type.charAt(0) != '(') {
            throw new InconsistencyException("invalid signature " + type);
        }

        int     pos = 1;

        _method_parameters_:
        for (;;) {
            switch (type.charAt(pos++)) {
            case ')':
                break _method_parameters_;

            case '[':
                while (type.charAt(pos) == '[') {
                    pos += 1;
                }
                if (type.charAt(pos) == 'L') {
                    while (type.charAt(pos) != ';') {
                        pos += 1;
                    }
                }
                pos += 1;

                used -= 1;
                break;

            case 'L':
                while (type.charAt(pos) != ';') {
                    pos += 1;
                }
                pos += 1;

                used -= 1;
                break;

            case 'Z':
            case 'B':
            case 'C':
            case 'S':
            case 'F':
            case 'I':
                used -= 1;
                break;

            case 'D':
            case 'J':
                used -= 2;
                break;

            default:
                throw new InconsistencyException("invalid signature " + type);
            }
        }

        switch (type.charAt(pos)) {
        case 'V':
            break;

        case 'Z':
        case 'B':
        case 'C':
        case 'S':
        case 'F':
        case 'I':
        case 'L':
        case '[':
            used += 1;
            break;

        case 'D':
        case 'J':
            used += 2;
            break;

        default:
            throw new InconsistencyException("invalid signature x" + type);
        }

        switch (getOpcode()) {
        case opc_invokevirtual:
        case opc_invokespecial:
            return used - 1;
        case opc_invokestatic:
            return used;
        default:
            throw new InconsistencyException("invalid opcode: " + getOpcode());
        }
    }

    // --------------------------------------------------------------------
    // WRITE
    // --------------------------------------------------------------------

    /**
     * Write this instruction into a file
     *
     * @param   cp      the constant pool that contain all data
     * @param   out     the file where to write this object info
     *
     * @exception   java.io.IOException an io problem has occured
     */
    /*package*/ void write(ConstantPool cp, DataOutput out) throws IOException {
        out.writeByte((byte)getOpcode());
        out.writeShort(method.getIndex());
    }

    // --------------------------------------------------------------------
    // DATA MEMBERS
    // --------------------------------------------------------------------

    private MethodRefConstant   method;
}
