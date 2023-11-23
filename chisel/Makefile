BUILD_DIR = ./build
DIFF_DIR = ./difftest/core
DIFF_WORK_DIR = ./difftest/test/test_workbench/sim

export PATH := $(PATH):$(abspath ./utils)

verilog:
	$(MAKE) clean
	mkdir -p $(BUILD_DIR)
	mill -i __.test.runMain Elaborate -td $(BUILD_DIR)

func:
	# cp $(BUILD_DIR)/PuaCpu.v $(DIFF_DIR)
	cd $(DIFF_WORK_DIR) && make func

test:
	@echo "make test"
	$(MAKE) clean
	mkdir -p $(BUILD_DIR)
	mill -i __.test.runMain TestMain -td $(BUILD_DIR)

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
	-rm -rf $(BUILD_DIR)

.PHONY: test verilog help compile bsp reformat checkformat clean

sim:
	@echo "Write this Makefile by yourself."

-include ../Makefile
