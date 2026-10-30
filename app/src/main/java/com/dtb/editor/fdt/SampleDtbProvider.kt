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
            operating-points-v2 = <0x51>;
        };

        cpu@500 {
            device_type = "cpu";
            compatible = "arm,cortex-a710";
            reg = <0x0 0x500>;
            enable-method = "psci";
            phandle = <0x6>;
            operating-points-v2 = <0x51>;
        };

        cpu@600 {
            device_type = "cpu";
            compatible = "arm,cortex-a710";
            reg = <0x0 0x600>;
            enable-method = "psci";
            phandle = <0x7>;
            operating-points-v2 = <0x51>;
        };

        cpu@700 {
            device_type = "cpu";
            compatible = "arm,cortex-x2";
            reg = <0x0 0x700>;
            enable-method = "psci";
            phandle = <0x8>;
            operating-points-v2 = <0x52>;
        };
    };

    thermal-zones {
        cpu-thermal {
            polling-delay-passive = <0xfa>;
            polling-delay = <0x3e8>;
            thermal-sensors = <0x80 0x0>;

            trips {
                cpu_alert0: trip-point@0 {
                    temperature = <0x14c08>;
                    hysteresis = <0x7d0>;
                    type = "passive";
                };
                cpu_crit: trip-point@1 {
                    temperature = <0x1adb0>;
                    hysteresis = <0x0>;
                    type = "critical";
                };
            };
        };
    };

    clocks {
        xo_board: xo-board {
            compatible = "fixed-clock";
            #clock-cells = <0x0>;
            clock-frequency = <0x124f800>;
        };
    };

    regulators {
        pm8350_s1: regulator-s1 {
            compatible = "regulator-fixed";
            regulator-name = "vdd_cpu_a510";
            regulator-min-microvolt = <0xb71b0>;
            regulator-max-microvolt = <0x10c8e0>;
        };
        pm8350_s2: regulator-s2 {
            compatible = "regulator-fixed";
            regulator-name = "vdd_cpu_a710";
            regulator-min-microvolt = <0xb71b0>;
            regulator-max-microvolt = <0x124f80>;
        };
    };
};
"""

    fun getSampleDtbBytes(): ByteArray {
        val parser = DtsParser()
        val compiler = FdtBinaryCompiler()
        val parsed = parser.parse(SAMPLE_DTS)
        return compiler.compile(parsed.rootNode, parsed.reservations)
    }
}
