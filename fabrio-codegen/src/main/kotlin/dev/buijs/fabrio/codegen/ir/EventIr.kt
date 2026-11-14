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

import dev.buijs.rialto.contract.Event
import dev.buijs.rialto.contract.Stream
import dev.buijs.rialto.contract.Subscription

/** An event of `events` (A1), with its payload and AsyncAPI channel resolved. */
data class EventIr(
    val name: String,
    val declaration: Event,
    val data: DeclarationRef,
    val channel: DocumentRef,
)

/** A stream of `streams` (A4), with its channel resolved. */
data class StreamIr(
    val name: String,
    val declaration: Stream,
    val channel: DocumentRef,
    /** The events it carries, by name. */
    val events: List<String>,
)

/** A subscription of `subscriptions` (A3). */
data class SubscriptionIr(
    val name: String,
    val declaration: Subscription,
    /** The event it consumes. */
    val event: String,
)
