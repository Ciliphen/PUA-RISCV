#import "../template/template.typ": *
#import "@preview/tablex:0.0.7": *

// Take a look at the file `template.typ` in the file panel
// to customize this template and discover how it works.
#show: project.with(
  title: "实验十 实现I型U型运算类指令的理想流水线设计实验",
  authors: (
    "Xi Lifeng",
  ),
)

#show math.equation: set text(font: "Linux Libertine")

#set par(justify: true, first-line-indent: 2em)
#show heading: it => {
    it
    par()[#text(size:0.5em)[#h(0.0em)]]
}

#show list: set list(indent: 1em)
#show enum: set enum(indent: 1em)

// 缩进控制
#let indent = h(2em)
#let noindent = h(-2em)
#let fakepar = par()[#text(size:0.5em)[#h(0.0em)]]

= 实验目的

+ 掌握I型和U型运算类指令的数据通路
+ 掌握在五级流水线中添加I型和U型指令的方法

= 实验原理与实验内容

#figure(
  tablex(
    columns: 33,
    align: center + horizon,
    repeat-header: true,
    header-rows: 1,
    auto-lines: false,

    vlinex(x:0, start:1), vlinex(x:7, start:1), vlinex(x:12, start:1), vlinex(x:17, start:1), vlinex(x:20, start:1), vlinex(x:25, start:1), vlinex(x:32, start:1),

    cellx(align: left, colspan: 6)[31], cellx(align: right)[25], cellx(align: left, colspan: 4)[24], cellx(align: right)[20], cellx(align: left, colspan: 4)[19], cellx(align: right)[15], cellx(align: left, colspan: 2)[14], cellx(align: right)[12], cellx(align: left, colspan: 4)[11], cellx(align: right)[7], cellx(align: left, colspan: 6)[6], cellx(align: right)[0], [],

    hlinex(end:32),

    colspanx(7, inset:(x: 5em))[funct7], colspanx(5, inset:(x: 2em))[rs2], colspanx(5, inset:(x: 2em))[rs1], colspanx(3)[funct3], colspanx(5, inset:(x: 2em))[rd], colspanx(7, inset:(x: 1.5em))[opcode], [R-type],

    hlinex(end:32),

    colspanx(12)[imm[11:0]], colspanx(5)[rs1], colspanx(3)[funct3], colspanx(5)[rd], colspanx(7)[opcode], [I-type], 

    hlinex(end:32),

    colspanx(7)[imm[15:5]], colspanx(5)[rs2], colspanx(5)[rs1], colspanx(3)[funct3], colspanx(5)[imm[4:0]], colspanx(7)[opcode], [S-type], 

    hlinex(end:32),

    colspanx(7)[imm[12|10:5]], colspanx(5)[rs2], colspanx(5)[rs1], colspanx(3)[funct3], colspanx(5)[imm[4:1|11]], colspanx(7)[opcode], [B-type], 

    hlinex(end:32),

    colspanx(20)[imm[31:12]], colspanx(5)[rd], colspanx(7)[opcode], [U-type], 

    hlinex(end:32),

    colspanx(20)[imm[20|10:1|11|19:12]], colspanx(5)[rd], colspanx(7)[opcode], [J-type], 

    hlinex(end:32),
  ),
  caption: "RISC-V指令格式",
  kind: table
)<RISC-V指令格式>

#figure(
  tablex(
    columns: 33,
    align: center + horizon,
    repeat-header: true,
    header-rows: 1,
    auto-lines: false,

    map-cells: cell => {
        if (cell.x == 32) {
          cell.content = {
            set align(left)
            cell.content
          }
        }
        cell
    },

    vlinex(x:0, start:1), vlinex(x:6, start:1), vlinex(x:7, start:1), vlinex(x:12, start:1), vlinex(x:17, start:1), vlinex(x:20, start:1), vlinex(x:25, start:1), vlinex(x:32, start:1),

    cellx(align: left, colspan: 5)[31], cellx(align: right)[26], [25], cellx(align: left, colspan: 4)[24], cellx(align: right)[20], cellx(align: left, colspan: 4)[19], cellx(align: right)[15], cellx(align: left, colspan: 2)[14], cellx(align: right)[12], cellx(align: left, colspan: 4)[11], cellx(align: right)[7], cellx(align: left, colspan: 6)[6], cellx(align: right)[0], [],

    hlinex(end:32),

    colspanx(12, inset:(x:7em))[imm[11:0]], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[000], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [ADDI], 

    hlinex(end:32),

    colspanx(12, inset:(x:7em))[imm[11:0]], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[010], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [SLTI], 

    hlinex(end:32),

    colspanx(12, inset:(x:7em))[imm[11:0]], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[011], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [SLTIU], 

    hlinex(end:32),

    colspanx(12, inset:(x:7em))[imm[11:0]], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[100], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [XORI], 

    hlinex(end:32),

    colspanx(12, inset:(x:7em))[imm[11:0]], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[110], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [ORI], 

    hlinex(end:32),

    colspanx(12, inset:(x:7em))[imm[11:0]], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[111], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [ANDI], 

    hlinex(end:32),

    colspanx(6, inset:(x:3.5em))[000000], colspanx(6, inset:(x:3.5em))[shamt], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[001], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [SLLI], 

    hlinex(end:32),

    colspanx(6, inset:(x:3.5em))[000000], colspanx(6, inset:(x:3.5em))[shamt], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[101], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [SRLI], 

    hlinex(end:32),

    colspanx(6, inset:(x:3.5em))[010000], colspanx(6, inset:(x:3.5em))[shamt], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[101], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0010011], [SRAI], 

    hlinex(end:32),

    colspanx(12, inset:(x:7em))[imm[11:0]], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[000], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0011011], [ADDIW], 

    hlinex(end:32),

    colspanx(7, inset:(x:4em))[0000000], colspanx(5, inset:(x:3em))[shamt], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[001], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0011011], [SLLIW], 

    hlinex(end:32),

    colspanx(7, inset:(x:4em))[0000000], colspanx(5, inset:(x:3em))[shamt], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[101], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0011011], [SRLIW], 

    hlinex(end:32),

    colspanx(7, inset:(x:4em))[0100000], colspanx(5, inset:(x:3em))[shamt], colspanx(5, inset:(x:2em))[rs1], colspanx(3)[101], colspanx(5, inset:(x:2em))[rd], colspanx(7)[0011011], [SRAIW], 

    hlinex(end:32),

  ),
  caption: "I型运算类指令",
  kind: table
)<I型运算类指令>

#figure(
  tablex(
    columns: 33,
    align: center + horizon,
    repeat-header: true,
    header-rows: 1,
    auto-lines: false,

    map-cells: cell => {
        if (cell.x == 32) {
          cell.content = {
            set align(left)
            cell.content
          }
        }
        cell
    },

    vlinex(x:0, start:1), vlinex(x:6, start:1), vlinex(x:7, start:1), vlinex(x:12, start:1), vlinex(x:17, start:1), vlinex(x:20, start:1), vlinex(x:25, start:1), vlinex(x:32, start:1),

    cellx(align: left, colspan: 19)[31], cellx(align: right)[12], cellx(align: left, colspan: 4)[11], cellx(align: right)[7], cellx(align: left, colspan: 6)[6], cellx(align: right)[0], [],

    hlinex(end:32),

    colspanx(20, inset:(x:12.4em))[imm[31:12]], colspanx(5, inset:(x:2.1em))[rd], colspanx(7)[0110111], [LUI], 

    hlinex(end:32),

    colspanx(20, inset:(x:12.4em))[imm[31:12]], colspanx(5, inset:(x:2.1em))[rd], colspanx(7)[0010111], [AUIPC], 

    hlinex(end:32),

  ),
  caption: "U型运算类指令",
  kind: table
)<U型运算类指令>

#fakepar



= 实验要求

= 实验步骤

= 思考与探索