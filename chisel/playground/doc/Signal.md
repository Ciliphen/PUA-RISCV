# 信号列表

## CSR 模块

### 信号

| 信号名         | 用处                                                                  |
| -------------- | --------------------------------------------------------------------- |
| wen            | CSR 写使能（write && !illegal_access 为真时，信号有效）               |
| addr           | CSR 地址                                                              |
| mode           | 当前模式                                                              |
| rdata          | 读到的 CSR 数据                                                       |
| wdata          | 写往 CSR 的数据                                                       |
| write          | 该指令为 CSR 写指令（即除 ECALL、ERET、EBREAK 这类 CSR 指令外的指令） |
| only_read      | 该 CSR 指令只进行读操作                                               |
| illegal_mode   | 当前模式低于要操作的 CSR 的最低模式                                   |
| illegal_write  | 当前写操作非法（指令为写指令且为非只读操作但访问的 CSR 权限为只读）   |
| illegal_access | 当前 CSR 访问非法（模式非法或写非法时，信号有效）                     |
| illegal_addr   | 要访问的 CSR 地址不存在                                               |

### 掩码

| 掩码名        | 数值                     |
| ------------- | ------------------------ |
| sstatus_wmask | h00000000000c6122        |
| sstatus_rmask | h80000003000de762        |
| sie_rmask     | "h222".U(64.W) & mideleg |
| sie_wmask     | "h222".U(64.W) & mideleg |
| sip_rmask     | "h222".U(64.W) & mideleg |
| sip_wmask     | "h222".U(64.W) & mideleg |

## Decoder Unit 模块

### InstInfo 结构体

| 信号名     | 用处                       |
| ---------- | -------------------------- |
| valid      | 指令有效                   |
| inst_legal | 指令合法，是已经实现的指令 |
| reg1_ren   | src1 读寄存器堆使能        |
| reg1_raddr | src1 访问寄存器堆地址      |
| reg2_ren   | src2 读寄存器堆使能        |
| reg2_raddr | src2 访问寄存器堆地址      |
| fusel      | FU 模块选择信号            |
| op         | FU 模块内部的操作码        |
| reg_wen    | 写回级是否写回寄存器堆     |
| reg_waddr  | 寄存器堆写回地址           |
| imm        | 立即数                     |
| inst       | 原指令                     |

### SrcInfo 结构体

| 信号名    | 用处                 |
| --------- | -------------------- |
| src1_data | 当前指令的源操作数 1 |
| src2_data | 当前指令的源操作数 2 |

### ExceptionInfo 结构体

| 信号名    | 用处                       |
| --------- | -------------------------- |
| exception | 当前指令的异常信息         |
| interrupt | 当前指令的中断信息         |
| tval      | 保留出现异常时的地址或指令 |
