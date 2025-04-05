package com.micro.kfschecker;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(name = "TextAreaAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class TextAreaAppender extends AbstractAppender {

    private static final Map<String, TextArea> textAreas = new ConcurrentHashMap<>();
    private final String textAreaId;

    private TextAreaAppender(String name, String textAreaId, PatternLayout layout, boolean ignoreExceptions) {
        super(name, null, layout, ignoreExceptions, null);
        this.textAreaId = textAreaId;
    }

    public static void setTextArea(String id, TextArea textArea) {
        textAreas.put(id, textArea);
    }

    @Override
    public void append(LogEvent event) {
        TextArea textArea = textAreas.get(textAreaId);
        if (textArea != null) {
            String message = getLayout().toSerializable(event).toString();
            //System.out.println("TextAreaAppender - Zpráva k přidání: " + message); // vypis do konzole - pro ladeni logovani
            Platform.runLater(() -> textArea.appendText(message));
        } /*else {
            System.out.println("TextAreaAppender - TextArea s ID '" + textAreaId + "' nebyl nalezen."); // vypis do konzole - pro ladeni logovani
        }*/
    }

    @PluginFactory
    public static TextAreaAppender createAppender(
            @PluginAttribute("name") final String name,
            @PluginAttribute("textAreaId") final String textAreaId,
            @PluginElement("Layout") PatternLayout layout,
            @PluginAttribute("ignoreExceptions") final boolean ignoreExceptions) {
        if (name == null) {
            LOGGER.error("No name provided for TextAreaAppender");
            return null;
        }
        if (textAreaId == null) {
            LOGGER.error("No textAreaId provided for TextAreaAppender");
            return null;
        }
        if (layout == null) {
            layout = PatternLayout.newBuilder().withPattern("%m%n").build();
        }
        return new TextAreaAppender(name, textAreaId, layout, ignoreExceptions);
    }
}