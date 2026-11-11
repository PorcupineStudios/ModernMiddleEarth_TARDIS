/**
 * The UI core: every management screen (a colony, all your colonies, permissions, the treasury, warehouses...) is a
 * {@link com.porcupinestudios.lotrport.ui.View} the server builds and the client draws with one screen,
 * {@code client.ui.UiScreen}. A view is tabs plus one page (a table or a list of stats) and the actions a player may
 * take; the server checks every action again. Add a screen by registering a {@link com.porcupinestudios.lotrport.ui.ViewProvider}
 * in {@link com.porcupinestudios.lotrport.ui.Views}; no client code is needed. See {@code lotr/docs/ui-core.md}.
 *
 * <p>Classes here are common (server and client). The table rules ({@link com.porcupinestudios.lotrport.ui.TableQuery})
 * are plain Java and unit-tested.
 */
package com.porcupinestudios.lotrport.ui;
