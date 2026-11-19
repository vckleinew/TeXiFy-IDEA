package org.gnome.evince

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface

/**
 * [Daemon] under the D-Bus namespace `org.gnome.Evince` that Evince uses since 49.
 */
@Suppress("FunctionName")
@DBusInterfaceName("org.gnome.Evince.Daemon")
interface Evince49Daemon : DBusInterface {

    /**
     * @see Daemon.FindDocument
     */
    fun FindDocument(uri: String?, spawn: Boolean): String?
}
