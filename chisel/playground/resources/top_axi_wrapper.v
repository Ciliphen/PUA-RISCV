module top_axi_wrapper(
    input       clock,
    input       reset,
    // Interrupts
    input       MEI, // to PLIC
    input       MSI, // to CLINT
    input       MTI, // to CLINT
    // aw
    output [3:0]MAXI_awid,
    output[31:0]MAXI_awaddr,
    output [7:0]MAXI_awlen,
    output [2:0]MAXI_awsize,
    output [1:0]MAXI_awburst,
    output      MAXI_awvalid,
    input       MAXI_awready,
    // w
    output[63:0]MAXI_wdata,
    output [7:0]MAXI_wstrb,
    output      MAXI_wlast,
    output      MAXI_wvalid,
    input       MAXI_wready,
    // b
    input  [3:0]MAXI_bid,
    input  [1:0]MAXI_bresp,
    input       MAXI_bvalid,
    output      MAXI_bready,
    // ar
    output [3:0]MAXI_arid,
    output[31:0]MAXI_araddr,
    output [7:0]MAXI_arlen,
    output [2:0]MAXI_arsize,
    output [1:0]MAXI_arburst,
    output      MAXI_arvalid,
    input       MAXI_arready,
    // r
    input  [3:0]MAXI_rid,
    input [63:0]MAXI_rdata,
    input  [1:0]MAXI_rresp,
    input       MAXI_rlast,
    input       MAXI_rvalid,
    output      MAXI_rready,
    // debug
    output      debug_commit,
    output[63:0]debug_pc,
    output[4:0] debug_reg_num,
    output[63:0]debug_wdata
);

PuaCpu core(
    .clock              (clock),
    .reset              (reset),
    // Interrupts
    .io_ext_int_ei      (MEI), // to PLIC
    .io_ext_int_si      (MSI), // to CLINT
    .io_ext_int_ti      (MTI), // to CLINT
    // aw
    .io_axi_aw_id       (MAXI_awid),
    .io_axi_aw_addr     (MAXI_awaddr),
    .io_axi_aw_len      (MAXI_awlen),
    .io_axi_aw_size     (MAXI_awsize),
    .io_axi_aw_burst    (MAXI_awburst),
    .io_axi_aw_valid    (MAXI_awvalid),
    .io_axi_aw_ready    (MAXI_awready),
    // w
    .io_axi_w_data      (MAXI_wdata),
    .io_axi_w_strb      (MAXI_wstrb),
    .io_axi_w_last      (MAXI_wlast),
    .io_axi_w_valid     (MAXI_wvalid),
    .io_axi_w_ready     (MAXI_wready),
    // b
    .io_axi_b_id        (MAXI_bid),
    .io_axi_b_resp      (MAXI_bresp),
    .io_axi_b_valid     (MAXI_bvalid),
    .io_axi_b_ready     (MAXI_bready),
    // ar
    .io_axi_ar_id       (MAXI_arid),
    .io_axi_ar_addr     (MAXI_araddr),
    .io_axi_ar_len      (MAXI_arlen),
    .io_axi_ar_size     (MAXI_arsize),
    .io_axi_ar_burst    (MAXI_arburst),
    .io_axi_ar_valid    (MAXI_arvalid),
    .io_axi_ar_ready    (MAXI_arready),
    // r
    .io_axi_r_id        (MAXI_rid),
    .io_axi_r_data      (MAXI_rdata),
    .io_axi_r_resp      (MAXI_rresp),
    .io_axi_r_last      (MAXI_rlast),
    .io_axi_r_valid     (MAXI_rvalid),
    .io_axi_r_ready     (MAXI_rready),
    // debug
    .debug_commit       (debug_commit),
    .debug_pc           (debug_pc),
    .debug_reg_num      (debug_reg_num),
    .debug_wdata        (debug_wdata)
);

endmodule