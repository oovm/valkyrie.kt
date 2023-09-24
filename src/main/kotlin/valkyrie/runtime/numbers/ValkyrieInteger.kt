/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package valkyrie.runtime.numbers

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import valkyrie.language.ValkyrieLanguage
import valkyrie.runtime.SLType
import java.math.BigDecimal
import java.math.BigInteger

@ExportLibrary(InteropLibrary::class)
class ValkyrieInteger : TruffleObject, Comparable<ValkyrieInteger> {
    @JvmField
    val value: BigInteger

    constructor(value: BigInteger) {
        this.value = value
    }

    constructor(value: Long) {
        this.value = BigInteger.valueOf(value)
    }

    @CompilerDirectives.TruffleBoundary
    override fun compareTo(o: ValkyrieInteger): Int {
        return value.compareTo(o.value)
    }

    @CompilerDirectives.TruffleBoundary
    override fun toString(): String {
        return value.toString()
    }

    @CompilerDirectives.TruffleBoundary
    override fun equals(obj: Any?): Boolean {
        if (obj is ValkyrieInteger) {
            return value == obj.value
        }
        return false
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    @get:ExportMessage
    val isNumber: Boolean
        get() = true

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun fitsInByte(): Boolean {
        return value.bitLength() < 8
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun fitsInShort(): Boolean {
        return value.bitLength() < 16
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun fitsInFloat(): Boolean {
        if (value.bitLength() <= 24) { // 24 = size of float mantissa + 1
            return true
        } else {
            val floatValue = value.toFloat()
            if (!java.lang.Float.isFinite(floatValue)) {
                return false
            }
            try {
                return BigDecimal(floatValue.toDouble()).toBigIntegerExact() == value
            } catch (e: ArithmeticException) {
                throw CompilerDirectives.shouldNotReachHere(e)
            }
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun fitsInLong(): Boolean {
        return value.bitLength() < 64
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun fitsInInt(): Boolean {
        return value.bitLength() < 32
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun fitsInDouble(): Boolean {
        if (value.bitLength() <= 53) { // 53 = size of double mantissa + 1
            return true
        } else {
            val doubleValue = value.toDouble()
            if (!java.lang.Double.isFinite(doubleValue)) {
                return false
            }
            try {
                return BigDecimal(doubleValue).toBigIntegerExact() == value
            } catch (e: ArithmeticException) {
                throw CompilerDirectives.shouldNotReachHere(e)
            }
        }
    }

    @ExportMessage
    fun fitsInBigInteger(): Boolean {
        return true
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @Throws(
        UnsupportedMessageException::class
    )
    fun asDouble(): Double {
        if (fitsInDouble()) {
            return value.toDouble()
        } else {
            throw UnsupportedMessageException.create()
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @Throws(
        UnsupportedMessageException::class
    )
    fun asLong(): Long {
        if (fitsInLong()) {
            return value.toLong()
        } else {
            throw UnsupportedMessageException.create()
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @Throws(
        UnsupportedMessageException::class
    )
    fun asByte(): Byte {
        if (fitsInByte()) {
            return value.toByte()
        } else {
            throw UnsupportedMessageException.create()
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @Throws(
        UnsupportedMessageException::class
    )
    fun asInt(): Int {
        if (fitsInInt()) {
            return value.toInt()
        } else {
            throw UnsupportedMessageException.create()
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @Throws(
        UnsupportedMessageException::class
    )
    fun asFloat(): Float {
        if (fitsInFloat()) {
            return value.toFloat()
        } else {
            throw UnsupportedMessageException.create()
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @Throws(
        UnsupportedMessageException::class
    )
    fun asShort(): Short {
        if (fitsInShort()) {
            return value.toShort()
        } else {
            throw UnsupportedMessageException.create()
        }
    }

    @ExportMessage
    fun asBigInteger(): BigInteger {
        return value
    }

    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }

    @get:ExportMessage
    val language: Class<out TruffleLanguage<*>?>
        get() = ValkyrieLanguage::class.java

    @ExportMessage
    fun hasMetaObject(): Boolean {
        return true
    }

    @get:ExportMessage
    val metaObject: Any
        get() = SLType.NUMBER

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun toDisplayString(@Suppress("unused") allowSideEffects: Boolean): Any {
        return value.toString()
    }
}
