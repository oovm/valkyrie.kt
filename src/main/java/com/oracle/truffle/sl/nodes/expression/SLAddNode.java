/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.nodes.expression;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.ValkyrieException;
import com.oracle.truffle.sl.nodes.SLBinaryNode;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode;
import com.oracle.truffle.sl.runtime.ValkyrieInteger;
import valkyrie.language.ValkyrieLanguage;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

/**
 * SL node that performs the "+" operation, which performs addition on arbitrary precision numbers,
 * as well as String concatenation if one of the operands is a String.
 * <p>
 * Type specialization on the input values is essential for the performance. This is achieved via
 * node rewriting: specialized subclasses handle just a single type, so that the generic node that
 * can handle all types is used only in cases where different types were encountered. The subclasses
 * are automatically generated by the Truffle DSL. In addition, a {@link SLAddNodeGen factory class}
 * is generated that provides, e.g., {@link SLAddNodeGen#create node creation}.
 */
@NodeInfo(shortName = "+")
public abstract class SLAddNode extends SLBinaryNode {

    /**
     * Specialization for primitive {@code long} values. This is the fast path of the
     * arbitrary-precision arithmetic. We need to check for overflows of the addition, and switch to
     * the {@link #add(ValkyrieInteger, ValkyrieInteger) slow path}. Therefore, we use an
     * {@link Math#addExact(long, long) addition method that throws an exception on overflow}. The
     * {@code rewriteOn} attribute on the {@link Specialization} annotation automatically triggers
     * the node rewriting on the exception.
     * <p>
     * In compiled code, {@link Math#addExact(long, long) addExact} is compiled to efficient machine
     * code that uses the processor's overflow flag. Therefore, this method is compiled to only two
     * machine code instructions on the fast path.
     * <p>
     * This specialization is automatically selected by the Truffle DSL if both the left and right
     * operand are {@code long} values.
     */
    @Specialization(rewriteOn = ArithmeticException.class)
    protected long doLong(long left, long right) {
        return Math.addExact(left, right);
    }

    /**
     * This is the slow path of the arbitrary-precision arithmetic. The {@link ValkyrieInteger} type of
     * Java is doing everything we need.
     * <p>
     * This specialization is automatically selected by the Truffle DSL if both the left and right
     * operand are {@link ValkyrieInteger} values. Because the type system defines an
     * {@link ImplicitCast implicit conversion} from {@code long} to {@link ValkyrieInteger} in
     * {@link SLTypes#castBigNumber(long)}, this specialization is also taken if the left or the
     * right operand is a {@code long} value. Because the {@link #add(long, long) long}
     * specialization} has the {@code rewriteOn} attribute, this specialization is also taken if
     * both input values are {@code long} values but the primitive addition overflows.
     */
    @Specialization
    @TruffleBoundary
    protected ValkyrieInteger doSLBigInteger(ValkyrieInteger left, ValkyrieInteger right) {
        return new ValkyrieInteger(left.value.add(right.value));
    }

    /**
     * This is the most general slow path of the arbitrary-precision arithmetic. In addition to what
     * {@link #doSLBigInteger(ValkyrieInteger, ValkyrieInteger)} can handle, it also handles foreign
     * objects that fit into {@link java.math.BigInteger}, e.g. host objects representing
     * {@link java.math.BigInteger} instances or big integer representations from other languages.
     * <p>
     * This specialization is automatically selected by the Truffle DSL if both the left and the
     * right operand {@link InteropLibrary#fitsInBigInteger(Object) fit} into
     * {@link java.math.BigInteger}, but at least one of them cannot be coverted to
     * {@link ValkyrieInteger} by {@link ImplicitCast implicit conversion}. Once this specialization
     * has been selected, it replaces the {@link #doSLBigInteger(ValkyrieInteger, ValkyrieInteger)}
     * specialization which is then never used again.
     */
    @Specialization(replaces = "doSLBigInteger", guards = {"leftLibrary.fitsInBigInteger(left)", "rightLibrary.fitsInBigInteger(right)"}, limit = "3")
    @TruffleBoundary
    protected ValkyrieInteger doInteropBigInteger(Object left, Object right,
                                                  @CachedLibrary("left") InteropLibrary leftLibrary,
                                                  @CachedLibrary("right") InteropLibrary rightLibrary) {
        try {
            return new ValkyrieInteger(leftLibrary.asBigInteger(left).add(rightLibrary.asBigInteger(right)));
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
    }

    /**
     * Specialization for TruffleString concatenation. The SL specification says that TruffleString
     * concatenation works if either the left or the right operand is a TruffleString. The
     * non-string operand is converted then automatically converted to a TruffleString.
     * <p>
     * To implement these semantics, we tell the Truffle DSL to use a custom guard. The guard
     * function is defined in {@link #isString this class}, but could also be in any superclass.
     */
    @Specialization(guards = "isString(left, right)")
    @TruffleBoundary
    protected static TruffleString doString(Object left, Object right,
                                            @Bind("this") Node node,
                                            @Cached SLToTruffleStringNode toTruffleStringNodeLeft,
                                            @Cached SLToTruffleStringNode toTruffleStringNodeRight,
                                            @Cached TruffleString.ConcatNode concatNode) {
        return concatNode.execute(toTruffleStringNodeLeft.execute(node, left), toTruffleStringNodeRight.execute(node, right), ValkyrieLanguage.STRING_ENCODING, true);
    }

    /**
     * Guard for TruffleString concatenation: returns true if either the left or the right operand
     * is a {@link TruffleString}.
     */
    protected boolean isString(Object a, Object b) {
        return a instanceof TruffleString || b instanceof TruffleString;
    }

    @Fallback
    protected Object typeError(Object left, Object right) {
        throw ValkyrieException.typeError(this, left, right);
    }
}
