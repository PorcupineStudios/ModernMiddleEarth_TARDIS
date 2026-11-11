/**
 * The society core (colony-first architecture, Aaron 2026-11-11): one registry of the NPCs that matter
 * ({@link com.porcupinestudios.lotrport.society.Persons}), each with an {@link com.porcupinestudios.lotrport.society.Owner}
 * (a player, a colony or a realm) and typed, versioned {@link com.porcupinestudios.lotrport.society.ComponentType
 * components}. Hired units, colonists, applicants and colony soldiers are all Persons; a feature adds a component type
 * and a system, never a field on Person or Colony. Everything outside this package asks {@link
 * com.porcupinestudios.lotrport.society.Society}, the facade, so storage can change without touching callers.
 */
package com.porcupinestudios.lotrport.society;
