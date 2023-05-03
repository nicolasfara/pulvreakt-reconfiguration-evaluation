/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.boundary.swingui.monitor.impl

import it.unibo.alchemist.boundary.interfaces.OutputMonitor
import it.unibo.alchemist.model.implementations.times.DoubleTime
import it.unibo.alchemist.model.interfaces.Actionable
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Molecule
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.Position
import it.unibo.alchemist.model.interfaces.Reaction
import it.unibo.alchemist.model.interfaces.Time
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 *
 * @param <P> position type
 * @param <T> concentration type
</T></P> */
@Deprecated("")
class NodeTracker<T, P : Position<out P>>(node: Node<T>) :
    JPanel(), OutputMonitor<T, P>, ActionListener {
    private val txt = JTextArea(AREA_SIZE / 2, AREA_SIZE)
    private val n: Node<T>
    private var stringLength = Byte.MAX_VALUE.toInt()
    private val updateIsScheduled = AtomicBoolean(false)

    @Volatile
    private var currentText: String = ""

    /**
     * @param node
     * the node to track
     */
    init {
        val areaScrollPane = JScrollPane(txt)
        n = node
        layout = BorderLayout(0, 0)
        txt.isEditable = false
        add(areaScrollPane, BorderLayout.CENTER)
        areaScrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        areaScrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    }

    override fun actionPerformed(e: ActionEvent) { }

    override fun finished(environment: Environment<T, P>, time: Time, step: Long) {
        stepDone(environment, null, time, step)
    }

    override fun initialized(environment: Environment<T, P>) {
        stepDone(environment, null, DoubleTime.ZERO, 0L)
    }

    override fun stepDone(
        environment: Environment<T, P>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long,
    ) {
        if (reaction == null || reaction is Reaction<*> && reaction.node == n) {
            val sb = StringBuilder(stringLength)
                .append(POSITION)
                .append('\n')
                .append(environment.getPosition(n))
                .append("\n\n\n")
                .append(CONTENT)
                .append('\n')
                .append(
                    n.contents.entries.stream()
                        .map { (key, value): Map.Entry<Molecule, T> -> key.name + " > " + value + '\n' }
                        .sorted()
                        .collect(Collectors.joining()),
                )
                .append("\n\n\n")
                .append(PROGRAM)
                .append("\n\n")
            for (r in n.reactions) {
                sb.append(r.toString()).append("\n\n")
            }
            stringLength = sb.length + MARGIN
            currentText = sb.toString()
            if (!updateIsScheduled.get()) {
                updateIsScheduled.set(true)
                scheduleUpdate()
            }
        }
    }

    private fun scheduleUpdate() {
        SwingUtilities.invokeLater {
            if (updateIsScheduled.getAndSet(false)) {
                txt.text = currentText
            }
        }
    }

    companion object {
        private const val MARGIN: Byte = 100
        private const val PROGRAM = " = Program ="
        private const val CONTENT = " = Content ="
        private const val POSITION = " = POSITION = "
        private const val serialVersionUID = -676002989218532788L
        private const val AREA_SIZE = 80
    }
}
