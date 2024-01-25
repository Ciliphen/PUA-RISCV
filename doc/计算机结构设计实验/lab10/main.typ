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

== I型与U型运算类指令列表

#fakepar
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

本实验需要在实验九的基础上继续增加两类运算类指令，分别是I型以及U型运算指令，其指令格式如@I型运算类指令 和@U型运算类指令 所示。

== 以ADDI指令为例

在实验九的实验中已经实现了R型运算类指令的数据通路，通过阅读RISC-V手册不难发现本实验需要实现的运算类指令与实验九中实现的指令功能十分相似，都是对两个操作数进行运算，将结果写入目的寄存器中，唯一的区别在于实验九的两个源操作数均来自通用寄存器堆，而本实验的源操作数还可能来自指令的立即数字段或是当前指令对应的PC值。

下面以ADDI指令为例，介绍如何在实验九的基础上完成数据通路的功能升级。@ADD指令定义 和@ADDI指令定义 展示了手册对ADD和ADDI指令的定义。

#fakepar
#figure(
  image("image/image-20240123172829004.png"),
  caption: "ADD指令定义"
)<ADD指令定义>

#figure(
  image("image/image-20240123172626822.png"),
  caption: "ADDI指令定义"
)<ADDI指令定义>
#fakepar

对比手册对ADD和ADDI指令的定义可以发现，两者的区别在于ADDI指令将源操作数2替换为了符号拓展的立即数。因此，可以复用实验九的大部分数据通路。

#let unitcnt = counter("unitcnt")
#let unitcnt_inc = {
  unitcnt.step()
  unitcnt.display()
}

#noindent #strong(text(12pt, red)[前端])

前端包括取指单元以及指令队列等部件，只负责指令的取回，因此无需进行修改。

#noindent #strong(text(12pt, red)[后端])

// #noindent #text(fill: blue)[（#unitcnt_inc）译码单元]

@支持R型运算类指令的译码单元 展示了目前已支持R型运算类指令的译码单元。两个源操作数被打包成src_info数据包发往执行单元。src_info数据包内包含src1_data和src2_data这两个源操作数，其被发往执行单元的ALU中进行相应的计算。ALU只会将两个源操作数根据不同的op进行不同的运算输出最终结果。因此，大部分R型计算指令的数据通路可以被复用，只需将译码单元中src2_data的值的来源从通用寄存器堆替换为经过符号拓展的立即数的值，并且还需将译码得到的op值设置的足够精巧，这样就可以不必改动后续数据通路并实现数据通路的复用。

综上所述，需要改动的地方有三点：

#[
    + 需要使用指令的立即数字段说明译码器需要译码生成imm信号。
    + 源操作数来源的增加意味着需要实现一个数据选择器，使得src2_data可以选择数据来自于通用寄存器堆还是立即数。
    + 对于op的设计，我们只需要将ADDI指令的op设置的与ADD的op一致，即可使ALU对ADDI指令也执行和ADD指令一样的操作。
]

#fakepar
#figure(
  image("../lab09/image/image-20240125134200367.png", width: 90%),
  caption: "支持R型运算类指令的译码单元"
)<支持R型运算类指令的译码单元>
#fakepar

#[
  #show figure: set block(breakable: true)
  #figure(
    tablex(
      columns: (1em, 10em, 20em),
      align: left + horizon,
      repeat-header: true,
      header-rows: 1,
      auto-lines: false,
      
      hlinex(y:0),
      hlinex(y:1),
      hlinex(y:11),

      map-cells: cell => {
          if (cell.y == 0) {
            cell.content = {
              set align(center)
              cell.content
            }
          }
          cell
      },

    [], [信号名], [含义],
    colspanx(2)[], text(red)[已经实现的信号],
    [], [src1_ren], [src1 是否需要读通用寄存器堆],
    [], [src1_raddr], [src1 的通用寄存器堆读地址],
    [], [src2_ren], [src2 是否需要读通用寄存器堆],
    [], [src2_raddr], [src2 的通用寄存器堆读地址],
    [], [op], [指令的操作类型],
    [], [reg_wen], [是否需要写回通用寄存器堆],
    [], [reg_waddr], [通用寄存器堆的写地址],
    colspanx(2)[], text(red)[需要增加的信号],
    [], [imm], [立即数]
    ),
    caption: "译码信号",
    kind: table
  )<译码信号>
]
#fakepar

已实现R型运算指令的处理器译码器产生的控制信号如@译码信号 所示。对于数据选择器的选择端，可以使用src2_ren进行控制数据的选择。对于ADD指令而言src2_ren为1，此时需要选择来自通用寄存器堆的src2_rdata数据；对于ADDI指令而言src2_ren为0，此时选择来自译码器的imm数据。因此只需要译码器进行修改，使得立即数指令译码得到的src2_ren为0即可。同时译码器需要将指令的立即数字段也就是31至20位进行符号拓展作为imm信号，输出到数据选择器的选择端。

#fakepar
#figure(
  image("image/image-20240125155402479.png"),
  caption: "支持ADDI指令的译码单元"
)<支持ADDI指令的译码单元>
#fakepar

修改完成的译码单元数据通路如@支持ADDI指令的译码单元 所示。数据选择器根据src2_ren选择数据得到src2_data。译码单元将src2_data和从通用寄存器读取到的src1_rdata一起打包成src_info数据包并与pc以及info数据包#footnote()[info数据包内包含op、reg_wen和reg_waddr]一块打包成data数据包发往下一级。

当data数据包来到执行单元后，由于ADDI指令的op与ADD指令的op一致，ALU会对ADDI指令的src1_data和src2_data同样进行ADD操作得到计算结果reg_wdata。数据继续向后流动经过访存级，最后在写回级更新通用寄存器堆。

= 实验要求

+ 在实验九的基础上继续实验，使CPU支持@I型运算类指令 和@U型运算类指令 中所列出的所有指令。
+ 通过本实验提供的所有仿真验证测试用例
+ 通过本实验提供的所有板级验证测试用例
+ 撰写实验报告：撰写报告时要求叙述以下内容，以及你对本实验的思考与探索。
  #[
    #set enum(numbering: "a）")
    + 选择@U型运算类指令 中列出的其中一条指令，按照你自己的理解，逐步介绍其数据通路设计的思路以及实现过程。
    + 更新上一实验中绘制完成的数据通路图。
    + 谈谈你对数据通路复用的理解。
  ] 
+ TODO：增加更多内容
= 实验步骤

+ 实验步骤参见实验九的实验步骤部分
+ 提醒与建议
  #[
    #set enum(numbering: "a）")
    + 实验的关键在于正确的在译码单元对源操作数进行选择。需要注意的是AUIPC指令的两个操作数分别是当前指令的PC和立即数；LUI指令只有一个源操作数，但也相当于是0与立即数进行运算。
    + 在chisel中有多种数据选择器类型，如Mux、MuxCase、MuxLookup、Mux1H等，请参考chisel教程章节选择适合的数据选择器。
    + 当reg_ren为0时，建议将reg_raddr置为0，防止CPU出现意想不到的状况。
  ]
= 思考与探索

+ 为什么在RISC-V指令中没有SUBI指令？
+ 观察@U型运算类指令 中指令的imm字段，为什么imm字段的长度被设计为20位？请问这样设计可以和哪些指令搭配使用并发挥什么样的效果？
+ 谈谈你在实验中碰到了哪些问题？又是如何解决的？