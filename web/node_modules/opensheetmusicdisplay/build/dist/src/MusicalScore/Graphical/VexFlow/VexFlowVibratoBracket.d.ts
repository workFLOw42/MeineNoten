import { WavyLine } from "../../VoiceData/Expressions/ContinuousExpressions/WavyLine";
import { BoundingBox } from "../BoundingBox";
import { GraphicalStaffEntry } from "../GraphicalStaffEntry";
import { GraphicalWavyLine } from "../GraphicalWavyLine";
import { VexFlowVoiceEntry } from "./VexFlowVoiceEntry";
import Vex from "vexflow";
export declare class VexFlowVibratoBracket extends GraphicalWavyLine {
    /** Defines the note where the bracket starts */
    startNote: Vex.Flow.StemmableNote;
    /** Defines the note where the bracket ends */
    endNote: Vex.Flow.StemmableNote;
    startVfVoiceEntry: VexFlowVoiceEntry;
    endVfVoiceEntry: VexFlowVoiceEntry;
    line: number;
    private isVibrato;
    private toEndOfStopStave;
    get ToEndOfStopStave(): boolean;
    constructor(wavyLine: WavyLine, parentBBox: BoundingBox, tabVibrato?: boolean);
    /**
     * Set a start note using a staff entry
     * @param graphicalStaffEntry the staff entry that holds the start note
     */
    setStartNote(graphicalStaffEntry: GraphicalStaffEntry): boolean;
    /**
     * Set an end note using a staff entry
     * @param graphicalStaffEntry the staff entry that holds the end note
     */
    setEndNote(graphicalStaffEntry: GraphicalStaffEntry): boolean;
    CalculateBoundingBox(): void;
    getVibratoBracket(): Vex.Flow.VibratoBracket;
}
