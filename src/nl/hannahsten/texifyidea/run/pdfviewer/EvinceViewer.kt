package nl.hannahsten.texifyidea.run.pdfviewer

import com.intellij.openapi.project.Project
import nl.hannahsten.texifyidea.TexifyBundle
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.NoReply
import org.freedesktop.dbus.errors.ServiceUnknown
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.types.UInt32
import org.gnome.evince.Daemon
import org.gnome.evince.Evince49Daemon
import org.gnome.evince.Evince49Window
import org.gnome.evince.SyncViewSourcePointStruct
import org.gnome.evince.Window

/**
 * Send commands to Evince.
 * For more information about D-Bus and forward/inverse search, see https://github.com/PHPirates/evince_dbus
 *
 * @author Thomas Schouten
 */
object EvinceViewer : SystemPdfViewer("Evince", "evince") {

    /**
     * The D-Bus names Evince exports. Evince 49 renamed its namespace from `org.gnome.evince` to `org.gnome.Evince`,
     * and D-Bus names are case-sensitive.
     */
    private enum class DBusApi(protected val daemonName: String, protected val daemonPath: String, protected val windowPath: String) {

        EVINCE_49("org.gnome.Evince.Daemon", "/org/gnome/Evince/Daemon", "/org/gnome/Evince/Window/0") {
            override fun findDocument(connection: DBusConnection, uri: String): String? =
                connection.getRemoteObject(daemonName, daemonPath, Evince49Daemon::class.java).FindDocument(uri, true)

            override fun syncView(connection: DBusConnection, owner: String, sourceFile: String, line: Int) =
                connection.getRemoteObject(owner, windowPath, Evince49Window::class.java)
                    .SyncView(sourceFile, SyncViewSourcePointStruct(line, -1), UInt32(0))
        },

        LEGACY("org.gnome.evince.Daemon", "/org/gnome/evince/Daemon", "/org/gnome/evince/Window/0") {
            override fun findDocument(connection: DBusConnection, uri: String): String? =
                connection.getRemoteObject(daemonName, daemonPath, Daemon::class.java).FindDocument(uri, true)

            override fun syncView(connection: DBusConnection, owner: String, sourceFile: String, line: Int) =
                connection.getRemoteObject(owner, windowPath, Window::class.java)
                    .SyncView(sourceFile, SyncViewSourcePointStruct(line, -1), UInt32(0))
        };

        /**
         * @return The bus name of the Evince process that has [uri] open, spawning one if needed.
         * @throws ServiceUnknown if this Evince version does not export this API.
         */
        abstract fun findDocument(connection: DBusConnection, uri: String): String?

        abstract fun syncView(connection: DBusConnection, owner: String, sourceFile: String, line: Int)
    }

    /**
     * This variable will hold the latest known Evince process owner. We need to know the owner of the pdf file in order to execute forward search.
     */
    private var processOwner: String? = null

    /** The API that returned [processOwner]. */
    private var processOwnerApi: DBusApi = DBusApi.EVINCE_49

    override fun openFile(pdfPath: String, project: Project, newWindow: Boolean, focusAllowed: Boolean, forceRefresh: Boolean, raiseOnError: Boolean): Pair<Boolean, String> {
        // Opening the file when not already open will lose focus, so we don't want to do that. However, we have to, otherwise manual forward search will not work
        return openFile(pdfPath, project)
    }

    /**
     * Open a file in Evince, starting it if it is not running yet. This also finds the process owner of the pdf, so we can execute forward search later.
     */
    fun openFile(pdfFilePath: String, project: Project): Pair<Boolean, String> {
        // Will do nothing if file is already open in Evince
        return findProcessOwner(pdfFilePath, project)
    }

    // This is not really correct since Evince will always focus on forward search, but we still want to show the option to users so that they can use it to disable forward search if they don't want to lose focus after compilation.
    override val isFocusSupported = true

    /**
     * Execute forward search, highlighting a certain line in Evince.
     * If a pdf file is given, it will execute FindDocument and open the pdf file again to find the latest process owner. If the pdf file is already open, this will do nothing.
     *
     * @param outputPath Full path to a pdf file.
     * @param sourceFilePath Full path to the LaTeX source file.
     * @param line Line number in the source file to highlight in the pdf.
     */
    override fun forwardSearch(outputPath: String?, sourceFilePath: String, line: Int, project: Project, focusAllowed: Boolean, raiseOnError: Boolean): Pair<Boolean, String> {
        // If we are not allowed to change focus, we cannot open the pdf or do forward search because this will always change focus with Evince
        if (!focusAllowed) {
            return Pair(false, "")
        }

        if (outputPath != null) {
            findProcessOwner(outputPath, project)
        }

        val owner = processOwner
        if (owner != null) {
            try {
                // Get DBusConnection
                DBusConnectionBuilder.forSessionBus().build().use { connection ->
                    // Sync the Evince view to the current position
                    try {
                        processOwnerApi.syncView(connection, owner, sourceFilePath, line)
                        return Pair(true, "")
                    }
                    catch (ignored: NoReply) {
                        return Pair(true, "")
                    }
                    catch (e: ServiceUnknown) {
                        return Pair(false, TexifyBundle.message("run.notification.evince.update.and.retry"))
                    }
                }
            }
            catch (e: DBusException) {
                return Pair(false, TexifyBundle.message("run.notification.connection.not.established"))
            }
        }
        else {
            // If the user used the forward search menu action
            return if (outputPath == null) {
                Pair(false, TexifyBundle.message("run.notification.forward.search.failed.compile.first.and.no.spaces"))
            }
            else {
                Pair(false, TexifyBundle.message("run.error.evince.forward.search.failed.detail", outputPath))
            }
        }
    }

    /**
     * Execute FindDocument on the D-Bus in order to find the process owner of the given pdf file, i.e. the process name which we can use for
     * forward/inverse search.
     * The value found will be saved for later use.
     *
     * @param pdfFilePath Full path to the pdf file to find the owner of.
     */
    private fun findProcessOwner(pdfFilePath: String, project: Project): Pair<Boolean, String> {
        try {
            // Get DBusConnection
            DBusConnectionBuilder.forSessionBus().build().use { connection ->
                for (api in DBusApi.entries) {
                    // Catch a NoReply, because it is unknown why Evince cannot start so we don't try to fix that
                    try {
                        processOwner = api.findDocument(connection, "file://$pdfFilePath")
                        processOwnerApi = api
                        return Pair(true, "")
                    }
                    catch (ignored: NoReply) {
                        return Pair(true, "")
                    }
                    catch (ignored: ServiceUnknown) {
                        // This Evince version exports the other API
                    }
                }
                return Pair(false, TexifyBundle.message("run.notification.evince.update.and.retry"))
            }
        }
        catch (e: DBusException) {
            return Pair(false, TexifyBundle.message("run.notification.connection.not.established"))
        }
    }
}
