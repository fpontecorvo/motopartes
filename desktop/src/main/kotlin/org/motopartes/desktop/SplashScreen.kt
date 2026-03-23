package org.motopartes.desktop

import java.awt.*
import javax.swing.ImageIcon
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JWindow
import javax.swing.SwingUtilities

class SplashScreen {
    private var window: JWindow? = null
    private var statusLabel: Label? = null

    fun show() {
        SwingUtilities.invokeAndWait {
            val iconUrl = Thread.currentThread().contextClassLoader.getResource("icon.png")
            val iconImage = if (iconUrl != null) ImageIcon(iconUrl).image.getScaledInstance(80, 80, Image.SCALE_SMOOTH) else null

            window = JWindow().apply {
                val panel = object : JPanel() {
                    override fun paintComponent(g: Graphics) {
                        super.paintComponent(g)
                        val g2 = g as Graphics2D
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                        // Background
                        g2.color = Color(0x1E, 0x1E, 0x1E)
                        g2.fillRect(0, 0, width, height)

                        // Border
                        g2.color = Color(0x3A, 0x3A, 0x3A)
                        g2.drawRect(0, 0, width - 1, height - 1)

                        // Icon
                        if (iconImage != null) {
                            g2.drawImage(iconImage, 20, 20, null)
                        }

                        // MOTOPARTES text
                        g2.font = Font("SansSerif", Font.BOLD, 36)
                        g2.color = Color(0xFF, 0xB7, 0x4D)
                        g2.drawString("MOTOPARTES", 112, 55)

                        // Version
                        g2.font = Font("SansSerif", Font.PLAIN, 13)
                        g2.color = Color(0xBD, 0xBD, 0xBD)
                        g2.drawString("v${org.motopartes.config.Version.NAME}", 112, 78)
                    }
                }
                panel.layout = BorderLayout()
                panel.preferredSize = Dimension(420, 130)

                val progressBar = JProgressBar().apply {
                    isIndeterminate = true
                    preferredSize = Dimension(420, 4)
                    background = Color(0x1E, 0x1E, 0x1E)
                    foreground = Color(0xFF, 0xB7, 0x4D)
                    isBorderPainted = false
                }

                statusLabel = Label("Iniciando...", Label.CENTER).apply {
                    foreground = Color(0xBD, 0xBD, 0xBD)
                    background = Color(0x1E, 0x1E, 0x1E)
                    font = Font("SansSerif", Font.PLAIN, 11)
                }

                val bottomPanel = JPanel(BorderLayout()).apply {
                    background = Color(0x1E, 0x1E, 0x1E)
                    add(statusLabel, BorderLayout.NORTH)
                    add(progressBar, BorderLayout.SOUTH)
                }

                panel.add(bottomPanel, BorderLayout.SOUTH)
                contentPane = panel
                pack()
                setLocationRelativeTo(null)
                isAlwaysOnTop = true
                isVisible = true
            }
        }
    }

    fun updateStatus(text: String) {
        SwingUtilities.invokeLater {
            statusLabel?.text = text
        }
    }

    fun close() {
        SwingUtilities.invokeLater {
            window?.isVisible = false
            window?.dispose()
            window = null
        }
    }
}
