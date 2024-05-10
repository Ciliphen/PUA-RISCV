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
- 可接入差分测试框架，提供软件仿真与板级测试（板级测试正在完善中...）
- 面向教学的实验设计，提供详细的实验指导手册

## 🛠️ 环境配置

```bash
git clone git@github.com:Ciliphen/riscv-lab.git
cd riscv-lab
git submodule update --init --recursive
```

## 📦 资源

1. 💾[PUA RISCV 文档](https://github.com/Ciliphen/riscv-doc) - 从零开始实现 PUA RISCV 处理器的指导手册

1. 🎨[Text to ASCII Art Generator](https://patorjk.com/software/taag/#p=testall&f=Graffiti&t=PUA-RISCV) - 字符画生成器

1. 🧰[RISC-V Convertor](https://luplab.gitlab.io/rvcodecjs/) - RISC-V 汇编转换器

1. 📑[Chisel Project Template](https://github.com/OSCPU/chisel-playground) - Chisel 项目模板

## 📈 进度

- [x] 实现 RV64IMAZicsr_Zifencei 指令集
- [x] 启动 OpenSBI
- [ ] 支持 Linux（目前存在 bug，正在修复中...）
