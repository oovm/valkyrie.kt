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
package com.oracle.truffle.sl.runtime

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Fallback
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.`object`.Shape
import com.oracle.truffle.api.strings.TruffleString
import com.oracle.truffle.api.utilities.TriState
import valkyrie.language.SLLanguage

/**
 * Represents an SL object.
 *
 *
 * This class defines operations that can be performed on SL Objects. While we could define all
 * these operations as individual AST nodes, we opted to define those operations by using
 * [a Truffle library][com.oracle.truffle.api.library.Library], or more concretely the
 * [InteropLibrary]. This has several advantages, but the primary one is that it allows SL
 * objects to be used in the interoperability message protocol, i.e. It allows other languages and
 * tools to operate on SL objects without necessarily knowing they are SL objects.
 *
 *
 * SL Objects are essentially instances of [DynamicObject] (objects whose members can be
 * dynamically added and removed). We also annotate the class with [ExportLibrary] with value
 * [InteropLibrary.class][InteropLibrary]. This essentially ensures that the build system and
 * runtime know that this class specifies the interop messages (i.e. operations) that SL can do on
 * [ValkyrieObject] instances.
 *
 * @see ExportLibrary
 *
 * @see ExportMessage
 *
 * @see InteropLibrary
 */
@ExportLibrary(InteropLibrary::class)
class ValkyrieObject(shape: Shape?) : DynamicObject(shape), TruffleObject {
    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }

    @get:ExportMessage
    val language: Class<out TruffleLanguage<*>?>
        get() = SLLanguage::class.java

    @ExportMessage
    @Suppress("unused")
    internal object IsIdenticalOrUndefined {
        @JvmStatic
        @Specialization
        fun doSLObject(receiver: ValkyrieObject, other: ValkyrieObject): TriState {
            return TriState.valueOf(receiver == other)
        }

        @JvmStatic
        @Fallback
        fun doOther(receiver: ValkyrieObject?, other: Any?): TriState {
            return TriState.UNDEFINED
        }
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun identityHashCode(): Int {
        return System.identityHashCode(this)
    }

    @ExportMessage
    fun hasMetaObject(): Boolean {
        return true
    }

    @get:ExportMessage
    val metaObject: Any
        get() = SLType.OBJECT

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun toDisplayString(@Suppress("unused") allowSideEffects: Boolean): Any {
        return "Object"
    }

    @ExportMessage
    fun hasMembers(): Boolean {
        return true
    }

    @ExportMessage
    @Throws(UnknownIdentifierException::class)
    fun removeMember(
        member: String?,
        @Cached @Cached.Shared("fromJavaStringNode") fromJavaStringNode: TruffleString.FromJavaStringNode,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ) {
        val memberTS = fromJavaStringNode.execute(member, SLLanguage.STRING_ENCODING)
        if (objectLibrary.containsKey(this, memberTS)) {
            objectLibrary.removeKey(this, memberTS)
        } else {
            throw UnknownIdentifierException.create(member)
        }
    }

    @ExportMessage
    fun getMembers(
        @Suppress("unused") includeInternal: Boolean,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ): Any {
        return Keys(objectLibrary.getKeyArray(this))
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    fun existsMember(
        member: String?,
        @Cached @Cached.Shared("fromJavaStringNode") fromJavaStringNode: TruffleString.FromJavaStringNode,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ): Boolean {
        return objectLibrary.containsKey(this, fromJavaStringNode.execute(member, SLLanguage.STRING_ENCODING))
    }

    @ExportMessage
    fun isMemberInsertable(
        member: String?,
        @CachedLibrary("this") receivers: InteropLibrary,
    ): Boolean {
        return !receivers.isMemberExisting(this, member)
    }

    @ExportLibrary(InteropLibrary::class)
    internal class Keys(private val keys: Array<Any>) : TruffleObject {
        @ExportMessage
        @Throws(InvalidArrayIndexException::class)
        fun readArrayElement(index: Long): Any {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index)
            }
            return keys[index.toInt()]
        }

        @ExportMessage
        fun hasArrayElements(): Boolean {
            return true
        }

        @get:ExportMessage
        val arraySize: Long
            get() = keys.size.toLong()

        @ExportMessage
        fun isArrayElementReadable(index: Long): Boolean {
            return index >= 0 && index < keys.size
        }
    }

    /**
     * [DynamicObjectLibrary] provides the polymorphic inline cache for reading properties.
     */
    @ExportMessage
    @Throws(UnknownIdentifierException::class)
    fun readMember(
        name: String?,
        @Cached @Cached.Shared("fromJavaStringNode") fromJavaStringNode: TruffleString.FromJavaStringNode,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ): Any {
        val result =
            objectLibrary.getOrDefault(this, fromJavaStringNode.execute(name, SLLanguage.STRING_ENCODING), null)
                ?: /* Property does not exist. */
                throw UnknownIdentifierException.create(name)
        return result
    }

    /**
     * [DynamicObjectLibrary] provides the polymorphic inline cache for writing properties.
     */
    @ExportMessage
    fun writeMember(
        name: String?, value: Any?,
        @Cached @Cached.Shared("fromJavaStringNode") fromJavaStringNode: TruffleString.FromJavaStringNode,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ) {
        objectLibrary.put(this, fromJavaStringNode.execute(name, SLLanguage.STRING_ENCODING), value)
    }

    companion object {
        protected const val CACHE_LIMIT: Int = 3
    }
}
