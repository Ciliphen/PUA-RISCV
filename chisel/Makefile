BUILD_DIR = ./build
DIFF_DIR = ../difftest/core
DIFF_WORK_DIR = ../difftest/

export PATH := $(PATH):$(abspath ./utils)

verilog:
	$(MAKE) clean
	mkdir -p $(BUILD_DIR)
	mill -i __.test.runMain Elaborate -td $(BUILD_DIR)

func:
	$(MAKE) verilog
	cp $(BUILD_DIR)/PuaCpu.v $(DIFF_DIR)
	$(MAKE) -C $(DIFF_WORK_DIR) func

trace:
	$(MAKE) verilog
	cp $(BUILD_DIR)/PuaCpu.v $(DIFF_DIR)
	$(MAKE) -C $(DIFF_WORK_DIR) trace
	cp $(DIFF_WORK_DIR)/trace.txt .

os:
	$(MAKE) -C $(DIFF_WORK_DIR) os

test:
	@echo "make test"
	$(MAKE) clean
	mkdir -p $(BUILD_DIR)
	mill -i __.test.runMain TestMain -td $(BUILD_DIR)

count:
	find ./playground/ -name "*.scala" | xargs wc -l 

help:
	mill -i __.test.runMain Elaborate --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	rm -rf $(BUILD_DIR)

.PHONY: test verilog help compile bsp reformat checkformat clean

sim:
	@echo "Write this Makefile by yourself."

-include ../Makefile
