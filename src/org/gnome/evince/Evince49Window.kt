package org.gnome.evince

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32

/**
 * [Window] under the D-Bus namespace `org.gnome.Evince` that Evince uses since 49.
 */
@Suppress("FunctionName")
@DBusInterfaceName("org.gnome.Evince.Window")
interface Evince49Window : DBusInterface {

    /**
     * @see Window.SyncView
     */
    fun SyncView(sourceFile: String?, sourcePoint: SyncViewSourcePointStruct?, timestamp: UInt32?)

    /**
     * @see Window.SyncSource
     */
    class SyncSource(
        path: String?,
        val sourceFile: String,
        val sourcePoint: SyncViewSourcePointStruct,
        timestamp: UInt32
    ) : DBusSignal(path, sourceFile, sourcePoint, timestamp)
}
