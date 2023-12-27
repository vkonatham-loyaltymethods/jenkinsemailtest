#!/bin/bash

mkdir -p target/site/cobertura && \
python ./SBI/cover2cover.py target/site/jacoco/jacoco.xml src/main/java > target/site/cobertura/coverage.xml
