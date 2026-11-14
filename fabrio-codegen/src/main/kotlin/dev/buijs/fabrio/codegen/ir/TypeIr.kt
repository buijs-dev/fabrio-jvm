/**
 * Copyright (c) 2021 - 2026 Buijs Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package dev.buijs.fabrio.codegen.ir

import dev.buijs.rialto.contract.Property
import dev.buijs.rialto.contract.Shape
import dev.buijs.rialto.contract.ShapeProperty
import dev.buijs.rialto.contract.TypeDecl

/** The kind of a named type (K2). */
enum class TypeKind { ENTITY, VALUE, ENUM, UNION, PROJECTION }

/** A type expression (K2.1) with its named types resolved to their kind. */
sealed class TypeRef {
    data class Scalar(
        val name: String,
        val precision: Int? = null,
        val scale: Int? = null,
    ) : TypeRef()

    /** A type of the contract; [ContractIr.type] gives its declaration. */
    data class Named(
        val name: String,
        val kind: TypeKind,
    ) : TypeRef()

    data class ListOf(
        val element: TypeRef,
        val nullableElements: Boolean,
    ) : TypeRef()

    data class SetOf(
        val element: TypeRef,
        val nullableElements: Boolean,
    ) : TypeRef()

    data class MapOf(
        val key: TypeRef,
        val value: TypeRef,
        val nullableValues: Boolean,
    ) : TypeRef()

    /** The named types this expression refers to, in order. */
    fun named(): List<Named> =
        when (this) {
            is Scalar -> emptyList()
            is Named -> listOf(this)
            is ListOf -> element.named()
            is SetOf -> element.named()
            is MapOf -> key.named() + value.named()
        }
}

/** A property of an entity or value object, with its type resolved. */
data class PropertyIr(
    val name: String,
    /** The type that declares it. */
    val owner: String,
    val declaration: Property,
    val type: TypeRef,
) {
    val nullable: Boolean get() = declaration.nullable
}

/** A named type of `types` (K2): the declaration, and its properties and references resolved. */
sealed class TypeIr {
    abstract val name: String
    abstract val kind: TypeKind
    abstract val declaration: TypeDecl

    /** The properties in contract order; none for an enum or union. */
    open val properties: Map<String, PropertyIr> get() = emptyMap()

    class Entity(
        override val name: String,
        override val declaration: TypeDecl.Entity,
        override val properties: Map<String, PropertyIr>,
    ) : TypeIr() {
        override val kind: TypeKind get() = TypeKind.ENTITY

        /** The identity properties (D2.2), in the order of `identity`. */
        val identity: List<PropertyIr> get() = declaration.identity.map(properties::getValue)
    }

    class Value(
        override val name: String,
        override val declaration: TypeDecl.Value,
        override val properties: Map<String, PropertyIr>,
        /** The type it wraps, for a single-value object. */
        val wraps: TypeRef?,
    ) : TypeIr() {
        override val kind: TypeKind get() = TypeKind.VALUE
    }

    class Enum(
        override val name: String,
        override val declaration: TypeDecl.Enum,
    ) : TypeIr() {
        override val kind: TypeKind get() = TypeKind.ENUM
        val members: List<String> get() = declaration.members
    }

    class Union(
        override val name: String,
        override val declaration: TypeDecl.Union,
        /** Per discriminator value, the variant type. */
        val variants: Map<String, TypeRef.Named>,
    ) : TypeIr() {
        override val kind: TypeKind get() = TypeKind.UNION
    }

    class Projection(
        override val name: String,
        override val declaration: TypeDecl.Projection,
        /** The entity or value object it projects. */
        val source: TypeRef.Named,
    ) : TypeIr() {
        override val kind: TypeKind get() = TypeKind.PROJECTION

        /** Per projection property, its source property path. */
        val paths: Map<String, String> get() = declaration.paths
    }
}

/** A property of a shape, with the source property it narrows (D3.5). */
data class ShapePropertyIr(
    val name: String,
    val declaration: ShapeProperty,
    /** The property of the shape's source type with the same name; null when the type has none. */
    val source: PropertyIr?,
    /** The shape that carries the value, for `as: command` or a nested shape. */
    val shape: String? = declaration.shape,
)

/** A shape of `shapes` (D3): a view of a source type, for a command or a response. */
data class ShapeIr(
    val name: String,
    val declaration: Shape,
    /** The type the shape is a view of. */
    val source: TypeRef.Named,
    val properties: Map<String, ShapePropertyIr>,
)

/** What an operation input, output or event payload names: a type or a shape. */
sealed class DeclarationRef {
    abstract val name: String

    data class Type(
        val type: TypeRef.Named,
    ) : DeclarationRef() {
        override val name: String get() = type.name
    }

    data class ShapeOf(
        override val name: String,
    ) : DeclarationRef()
}
