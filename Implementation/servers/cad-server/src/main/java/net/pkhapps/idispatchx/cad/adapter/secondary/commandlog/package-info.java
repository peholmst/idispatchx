/**
 * File-based adapter for the command audit log secondary port.
 * <p>
 * Provides {@link net.pkhapps.idispatchx.cad.adapter.secondary.commandlog.FileBasedCommandLogAdapter},
 * the default implementation of
 * {@link net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogPort}, which writes
 * commands to an append-only text file with size-based rotation.
 */
@NullMarked
package net.pkhapps.idispatchx.cad.adapter.secondary.commandlog;

import org.jspecify.annotations.NullMarked;
