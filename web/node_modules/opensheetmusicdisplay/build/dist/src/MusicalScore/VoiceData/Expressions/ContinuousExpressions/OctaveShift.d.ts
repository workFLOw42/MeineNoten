import { MultiExpression } from "../MultiExpression";
import { Pitch } from "../../../../Common/DataObjects/Pitch";
export declare class OctaveShift {
    constructor(type: string, octave: number);
    private octaveValue;
    private staffNumber;
    numberXml: number;
    /** Index of the first VoiceEntry at the end timestamp that is NOT covered by this shift.
     *  Used when a stop falls between grace notes sharing the same timestamp.
     *  0 means no VoiceEntries at the end timestamp are covered. */
    endVoiceEntryIndex: number;
    private startMultiExpression;
    private endMultiExpression;
    get Type(): OctaveEnum;
    set Type(value: OctaveEnum);
    get StaffNumber(): number;
    set StaffNumber(value: number);
    get ParentStartMultiExpression(): MultiExpression;
    set ParentStartMultiExpression(value: MultiExpression);
    get ParentEndMultiExpression(): MultiExpression;
    set ParentEndMultiExpression(value: MultiExpression);
    private setOctaveShiftValue;
    /**
     * Convert a source (XML) pitch of a note to the pitch needed to draw. E.g. 8va would draw +1 octave so we reduce by 1
     * @param pitch Original pitch
     * @param octaveShiftValue octave shift
     * @returns New pitch with corrected octave shift
     */
    static getPitchFromOctaveShift(pitch: Pitch, octaveShiftValue: OctaveEnum): Pitch;
}
export declare enum OctaveEnum {
    VA8 = 0,
    VB8 = 1,
    MA15 = 2,
    MB15 = 3,
    NONE = 4
}
