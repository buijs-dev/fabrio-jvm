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
package dev.buijs.fabrio.examples.shipment.domain


/**
 * Implement this interface as one application CDI bean (@ApplicationScoped).
 * A missing implementation fails the Quarkus build: unsatisfied dependency.
 */
public interface ShipmentV2CustomMapping {
    fun labelToDto(source: dev.buijs.fabrio.examples.shipment.domain.Shipment): kotlin.String
    fun legacyReferenceToDto(source: dev.buijs.fabrio.examples.shipment.domain.Shipment): kotlin.String
}
