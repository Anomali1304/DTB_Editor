package com.dtb.editor.fdt

import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler

object SampleDtbProvider {

    const val SAMPLE_DTS = """/dts-v1/;

/memreserve/ 0x0000000080000000 0x0000000000100000;

/ {
    model = "Qualcomm Technologies, Inc. SM8450 v2";
    compatible = "qcom,sm8450", "qcom,waipio";
    #address-cells = <0x2>;
    #size-cells = <0x2>;
    interrupt-parent = <0x90>;

    aliases {
        serial0 = "/soc@0/serial@990000";
    };

    chosen {
        bootargs = "console=ttyMSM0,115200n8 earlycon=msm_geni_serial,0x990000 androidboot.hardware=qcom";
    };

    memory@80000000 {
        device_type = "memory";
        reg = <0x0 0x80000000 0x2 0x0>;
    };

    cpus {
        #address-cells = <0x2>;
        #size-cells = <0x0>;

        cpu-map {
            cluster0 {
                core0 {
                    cpu = <0x1>;
                };
                core1 {
                    cpu = <0x2>;
                };
                core2 {
                    cpu = <0x3>;
                };
                core3 {
                    cpu = <0x4>;
                };
            };
            cluster1 {
                core0 {
                    cpu = <0x5>;
                };
                core1 {
                    cpu = <0x6>;
                };
                core2 {
                    cpu = <0x7>;
                };
                core3 {
                    cpu = <0x8>;
                };
            };
        };

        cpu@0 {
            device_type = "cpu";
            compatible = "arm,cortex-a510";
            reg = <0x0 0x0>;
            enable-method = "psci";
            phandle = <0x1>;
            operating-points-v2 = <0x50>;
            cpu-idle-states = <0x60>;
        };

        cpu@100 {
            device_type = "cpu";
            compatible = "arm,cortex-a510";
            reg = <0x0 0x100>;
            enable-method = "psci";
            phandle = <0x2>;
            operating-points-v2 = <0x50>;
        };

        cpu@200 {
            device_type = "cpu";
            compatible = "arm,cortex-a510";
            reg = <0x0 0x200>;
            enable-method = "psci";
            phandle = <0x3>;
            operating-points-v2 = <0x50>;
        };

        cpu@300 {
            device_type = "cpu";
            compatible = "arm,cortex-a510";
            reg = <0x0 0x300>;
            enable-method = "psci";
            phandle = <0x4>;
            operating-points-v2 = <0x50>;
        };

        cpu@400 {
            device_type = "cpu";
            compatible = "arm,cortex-a710";
            reg = <0x0 0x400>;
            enable-method = "psci";
            phandle = <0x5>;
            operating-points-v2 = <0x50>;
        };

        cpu@500 {
            device_type = "cpu";
            compatible = "arm,cortex-a710";
            reg = <0x0 0x500>;
            enable-method = "psci";
            phandle = <0x6>;
            operating-points-v2 = <0x50>;
        };

        cpu@600 {
            device_type = "cpu";
            compatible = "arm,cortex-a710";
            reg = <0x0 0x600>;
            enable-method = "psci";
            phandle = <0x7>;
            operating-points-v2 = <0x50>;
        };

        cpu@700 {
            device_type = "cpu";
            compatible = "arm,cortex-X2";
            reg = <0x0 0x700>;
            enable-method = "psci";
            phandle = <0x8>;
            operating-points-v2 = <0x50>;
        };
    };

    soc@0 {
        compatible = "simple-bus";
        #address-cells = <0x2>;
        #size-cells = <0x2>;
        ranges;

        serial@990000 {
            compatible = "qcom,geni-uart";
            reg = <0x0 0x990000 0x0 0x4000>;
            interrupts = <0x0 0x25 0x4>;
            clocks = <0x51 0x0>, <0x51 0x1>;
            clock-names = "se", "m-ahb";
        };

        power-controller@11bc00 {
            compatible = "mediatek,cpufreq-hw";
            reg = <0x0 0x11bc10 0x0 0x120 0x0 0x11bd30 0x0 0x120>;
            reg-names = "performance-domain0", "performance-domain1";
            phandle = <0x90>;
        };
    };
};
"""

    fun getSampleDtbBytes(): ByteArray {
        val parseResult = DtsParser().parse(SAMPLE_DTS)
        return FdtBinaryCompiler().compile(parseResult.rootNode, parseResult.reservations)
    }
}
