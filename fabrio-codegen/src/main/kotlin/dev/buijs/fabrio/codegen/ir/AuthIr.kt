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

import dev.buijs.rialto.contract.Auth

/** The `auth` section (S1), with the OpenAPI security schemes of scopes and API keys resolved. */
data class AuthIr(
    val declaration: Auth,
    /** Per scope, its security scheme. */
    val scopeSchemes: Map<String, DocumentRef>,
    /** Per API key, its security scheme (S6). */
    val apiKeySchemes: Map<String, DocumentRef>,
)
