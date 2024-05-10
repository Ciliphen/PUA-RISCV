module top_axi_wrapper(
    input       clock,
    input       reset,
    // Interrupts
    input       MEI, // to PLIC
    input       MSI, // to CLINT
    input       MTI, // to CLINT
    input       SEI, // to PLIC
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
    output[4:0] debug_rf_wnum,
    output[63:0]debug_rf_wdata,
    // debug csr
    output      debug_csr_interrupt,
    output[63:0]debug_csr_mcycle,
    output[63:0]debug_csr_mip
);

PuaCpu core(
    .clock                    (clock),
    .reset                    (reset),
    // Interrupts     
    .io_ext_int_mei           (MEI), // to PLIC
    .io_ext_int_msi           (MSI), // to CLINT
    .io_ext_int_mti           (MTI), // to CLINT
    .io_ext_int_sei           (SEI), // to PLIC
    // aw 
    .io_axi_aw_bits_id        (MAXI_awid),
    .io_axi_aw_bits_addr      (MAXI_awaddr),
    .io_axi_aw_bits_len       (MAXI_awlen),
    .io_axi_aw_bits_size      (MAXI_awsize),
    .io_axi_aw_bits_burst     (MAXI_awburst),
    .io_axi_aw_valid          (MAXI_awvalid),
    .io_axi_aw_ready          (MAXI_awready),
    // w 
    .io_axi_w_bits_data       (MAXI_wdata),
    .io_axi_w_bits_strb       (MAXI_wstrb),
    .io_axi_w_bits_last       (MAXI_wlast),
    .io_axi_w_valid           (MAXI_wvalid),
    .io_axi_w_ready           (MAXI_wready),
    // b 
    .io_axi_b_bits_id         (MAXI_bid),
    .io_axi_b_bits_resp       (MAXI_bresp),
    .io_axi_b_valid           (MAXI_bvalid),
    .io_axi_b_ready           (MAXI_bready),
    // ar 
    .io_axi_ar_bits_id        (MAXI_arid),
    .io_axi_ar_bits_addr      (MAXI_araddr),
    .io_axi_ar_bits_len       (MAXI_arlen),
    .io_axi_ar_bits_size      (MAXI_arsize),
    .io_axi_ar_bits_burst     (MAXI_arburst),
    .io_axi_ar_valid          (MAXI_arvalid),
    .io_axi_ar_ready          (MAXI_arready),
    // r 
    .io_axi_r_bits_id         (MAXI_rid),
    .io_axi_r_bits_data       (MAXI_rdata),
    .io_axi_r_bits_resp       (MAXI_rresp),
    .io_axi_r_bits_last       (MAXI_rlast),
    .io_axi_r_valid           (MAXI_rvalid),
    .io_axi_r_ready           (MAXI_rready),
    // debug
    .io_debug_pc              (debug_pc),
    .io_debug_commit          (debug_commit),
    .io_debug_rf_wnum         (debug_rf_wnum),
    .io_debug_rf_wdata        (debug_rf_wdata),
    // debug csr
    .io_debug_csr_mcycle      (debug_csr_mcycle),
    .io_debug_csr_mip         (debug_csr_mip),
    .io_debug_csr_interrupt   (debug_csr_interrupt)
);

endmodule