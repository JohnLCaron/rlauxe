/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.xml;

import org.cryptobiotic.rlauxe.util.Indent;

import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class TimeStaxReading {

    @Test
    public void testXml() throws XMLStreamException, IOException {
        String xmlFile = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/SF2024/summary.xml";
        new RunStaxReading(xmlFile);
    }

}

class RunStaxReading {
    static boolean show = false, process = true, showFields = false;

    RunStaxReading(String filename) throws FileNotFoundException, XMLStreamException {
        var indent = new Indent(0);

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty("javax.xml.stream.isCoalescing", Boolean.TRUE);
        XMLEventReader reader = xmlInputFactory.createXMLEventReader(new FileInputStream(filename));

        while (reader.hasNext()) {
            XMLEvent nextEvent = reader.nextEvent();

            if (nextEvent.isStartElement()) {
                StartElement elem = nextEvent.asStartElement();
                showElem(elem, indent);
                indent = indent.incr();

            } else if (nextEvent.isEndElement()) {
                EndElement elem = nextEvent.asEndElement();
                String name = elem.getName().getLocalPart();
                indent = indent.decr();
                System.out.printf("%s %s\n", indent, name);
            }
        }

    }


    void showElem(StartElement elem, Indent indent) {
        String name = elem.getName().getLocalPart();
        System.out.printf("%s %s\n", indent, name);
        Iterator iter = elem.getAttributes();
        while (iter.hasNext()) {
            Attribute attr = (Attribute) iter.next();
            System.out.printf("%s    @%s\n", indent, attr);
        }
    }

  /*
  void readElement() throws XMLStreamException {
    tab++;
    indent();
    if (show)
      System.out.print(r.getLocalName());

    int natts = r.getAttributeCount();
    String fldName = null;
    for (int i = 0; i < natts; i++) {
      String name = r.getAttributeLocalName(i);
      String val = r.getAttributeValue(i);
      if (show)
        System.out.print(" " + name + "='" + val + "'");
      if (name.equals("name"))
        fldName = val;
    }
    if (show)
      System.out.println();
    if (!readFields && r.getLocalName().equals("data")) {
      if (MetarField.fields.get(fldName) != null)
        readFields = true;
      else
        new MetarField(fldName);
    }

    while (r.hasNext() && (nmetars < nelems)) {
      int eventType = r.next();
      if (XMLStreamReader.END_ELEMENT == eventType)
        break;
      else if (XMLStreamReader.START_ELEMENT == eventType)
        readElement();
      else if (XMLStreamReader.CHARACTERS == eventType) {
        String text = r.hasText() ? r.getText().trim() : "";
        if (process && text.length() > 0) {
          MetarField fld = MetarField.fields.get(fldName);
          if (null != fld)
            fld.sum(text);
          indent();
          if (show)
            System.out.println("  text=(" + text + ")");
        }
      } else {
        String text = r.hasText() ? r.getText().trim() : "";
        String name = r.hasName() ? r.getLocalName() : "";
        indent();
        if (show)
          System.out.print(eventName(eventType) + ": " + name);
        if (text.length() > 0)
          if (show)
            System.out.print(" text=(" + text + ")");
        if (show)
          System.out.println();
      }
    }
    tab--;
    // if (count % 1000 == 0) System.out.println("did " + count);
  } */

}


