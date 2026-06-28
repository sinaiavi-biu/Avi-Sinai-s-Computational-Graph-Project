# Config File Examples

Use these files in the Assignment 6 upload form.

Valid files:

- `plus_only.conf`: publish `A=4`, `B=6`, expect `C=10.0`.
- `inc_only.conf`: publish `A=4`, expect `B=5.0`.
- `two_stage_pipeline.conf`: publish `A=4`, `B=6`, expect `C=10.0`, `D=11.0`.
- `long_inc_chain.conf`: publish `A=1`, expect `B=2.0`, `C=3.0`, `D=4.0`, `E=5.0`.
- `branching_plus.conf`: publish `A=2`, `B=3`, `C=4`, expect `SUM1=5.0`, `SUM2=6.0`, `TOTAL=11.0`.
- `mixed_math.conf`: publish `A=2`, `B=3`, `C=10`, expect `AB=5.0`, `AB_PLUS_1=6.0`, `RESULT=16.0`.
- `math_family.conf`: publish `A=6`, `B=3`, expect examples such as `SUB_OUT=3.0`, `MUL_OUT=18.0`, `DIV_OUT=2.0`, `POW_OUT=216.0`.
- `boolean_gates.conf`: publish `A=1`, `B=0`, `C=1`, expect examples such as `AND_OUT=0`, `OR_OUT=1`, `XOR_OUT=1`, `NAND_OUT=1`, `NOR_OUT=0`, `XNOR_OUT=0`, `NOT_A=0`, `MAJ_OUT=1`.
- `plus_many_inputs.conf`: publish `A=1`, `B=2`, `C=3`, `D=4`, `E=5`, `F=6`, `G=7`, `H=8`, `I=9`, `J=10`, expect `TOTAL=55.0`.
- `boolean_many_inputs.conf`: publish `A=1`, `B=1`, `C=0`, `D=1`, expect `ALL_TRUE=0`, `ANY_TRUE=1`, `ODD_TRUE=1`.
- `fan_in.conf`: publish `A=4`, `B=7`, expect `A1=5.0`, `B1=8.0`, `TOTAL=13.0`.
- `cycle_demo.conf`: useful for graph visualization of a cycle. Be careful publishing into it because the agents can keep triggering each other.
- `whitespace_valid.conf`: same as simple pipeline, but with blank lines and spaces to verify trimming.

Invalid files:

- `invalid_missing_line.conf`: number of meaningful lines is not divisible by 3.
- `invalid_class.conf`: class name does not exist.
- `invalid_agent_args.conf`: `PlusAgent` receives only one input topic, so constructor validation fails.
