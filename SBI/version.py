#!/usr/bin/python
import xml.etree.ElementTree as ElementTree
ns = '{http://maven.apache.org/POM/4.0.0}'
ver = ElementTree.parse('pom.xml')
print(ver.find(ns+'version').text)
