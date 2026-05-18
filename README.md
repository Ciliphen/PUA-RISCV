```
██████╗ ██╗   ██╗ █████╗       ██████╗ ██╗███████╗ ██████╗██╗   ██╗
██╔══██╗██║   ██║██╔══██╗      ██╔══██╗██║██╔════╝██╔════╝██║   ██║
██████╔╝██║   ██║███████║█████╗██████╔╝██║███████╗██║     ██║   ██║
██╔═══╝ ██║   ██║██╔══██║╚════╝██╔══██╗██║╚════██║██║     ╚██╗ ██╔╝
██║     ╚██████╔╝██║  ██║      ██║  ██║██║███████║╚██████╗ ╚████╔╝
╚═╝      ╚═════╝ ╚═╝  ╚═╝      ╚═╝  ╚═╝╚═╝╚══════╝ ╚═════╝  ╚═══╝
```

# 🚀 PUA (Powerful Ultimate Architecture) RISCV

本项目为 PUA-CPU 的 RISC-V 线

PUA-CPU 的 MIPS 线详见 [PUA-MIPS](https://github.com/Clo91eaf/PUA-MIPS)

## 📚 简介

- 支持 RV64IMAZicsr_Zifencei 指令集的顺序动态双发射五级流水线
- 可接入[差分测试框架](https://github.com/Ciliphen/riscv-difftest)，提供软件仿真

## 🛠️ 环境配置

```bash
git clone git@github.com:Ciliphen/PUA-RISCV.git
cd PUA-RISCV
git submodule update --init --recursive
```

## 📦 资源

1. 🎨[Text to ASCII Art Generator](https://patorjk.com/software/taag/#p=testall&f=Graffiti&t=PUA-RISCV) - 字符画生成器

1. 🧰[RISC-V Convertor](https://luplab.gitlab.io/rvcodecjs/) - RISC-V 汇编转换器

1. 📑[Chisel Project Template](https://github.com/OSCPU/chisel-playground) - Chisel 项目模板

## 📈 进度

- [x] 实现 RV64IMAZicsr_Zifencei 指令集
- [x] 启动 OpenSBI
- [x] 支持 Linux (内核版本5.2.11)

## 🖼️ 启动展示

### OpenSBI 启动

![OpenSBI 启动截图](opensbi-boot.png)

### Linux 启动

![Linux 启动截图](linux-boot.png)
