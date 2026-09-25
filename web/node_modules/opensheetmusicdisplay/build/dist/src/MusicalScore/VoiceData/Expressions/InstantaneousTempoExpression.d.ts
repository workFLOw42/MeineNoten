import { AbstractTempoExpression } from "./AbstractTempoExpression";
import { PlacementEnum } from "./AbstractExpression";
import { Fraction } from "../../../Common/DataObjects/Fraction";
import { MultiTempoExpression } from "./MultiTempoExpression";
/** A single note in a complex metronome mark (e.g. swing notation: two eighth notes = quarter + eighth under triplet). */
export interface MetronomeNote {
    /** MusicXML note type string, e.g. "quarter", "eighth" */
    type: string;
    /** Number of dots on this note */
    dots: number;
    /** Beam state for this note: "begin", "continue", "end", or undefined */
    beam?: string;
}
/** Tuplet bracket information for a group of metronome notes. */
export interface MetronomeTuplet {
    /** Actual notes in the tuplet (e.g. 3 for triplet) */
    actualNotes: number;
    /** Normal notes the tuplet replaces (e.g. 2 for triplet) */
    normalNotes: number;
    /** Whether to draw a bracket */
    bracket: boolean;
    /** What number to show: "actual", "both", "none" */
    showNumber: string;
}
/** A group of metronome notes on one side of a note equation, with optional tuplet. */
export interface MetronomeNoteGroup {
    notes: MetronomeNote[];
    tuplet?: MetronomeTuplet;
}
/** Tempo expressions that usually have an instantaneous and non-gradual effect on playback speed (e.g. Allegro),
 * or at least cover large sections, compared to the usually gradual effects or shorter sections of ContinuousExpressions.
 */
export declare class InstantaneousTempoExpression extends AbstractTempoExpression {
    constructor(label: string, placement: PlacementEnum, staffNumber: number, soundTempo: number, parentMultiTempoExpression: MultiTempoExpression, isMetronomeMark?: boolean);
    dotted: boolean;
    beatUnit: string;
    isMetronomeMark: boolean;
    /** For complex metronome marks (note equations like swing): left-side note group */
    metronomeNoteGroupLeft: MetronomeNoteGroup;
    /** For complex metronome marks (note equations like swing): right-side note group */
    metronomeNoteGroupRight: MetronomeNoteGroup;
    /** The relation between left and right groups, e.g. "equals" */
    metronomeRelation: string;
    private static listInstantaneousTempoLarghissimo;
    private static listInstantaneousTempoGrave;
    private static listInstantaneousTempoLento;
    private static listInstantaneousTempoLargo;
    private static listInstantaneousTempoLarghetto;
    private static listInstantaneousTempoAdagio;
    private static listInstantaneousTempoAdagietto;
    private static listInstantaneousTempoAndanteModerato;
    private static listInstantaneousTempoAndante;
    private static listInstantaneousTempoAndantino;
    private static listInstantaneousTempoModerato;
    private static listInstantaneousTempoAllegretto;
    private static listInstantaneousTempoAllegroModerato;
    private static listInstantaneousTempoAllegro;
    private static listInstantaneousTempoVivace;
    private static listInstantaneousTempoVivacissimo;
    private static listInstantaneousTempoAllegrissimo;
    private static listInstantaneousTempoPresto;
    private static listInstantaneousTempoPrestissimo;
    private static listInstantaneousTempoChangesGeneral;
    private static listInstantaneousTempoAddons;
    TempoType: TempoType;
    InstTempo: InstTempo;
    /** This is `none` unless `TempoType` is `change`. */
    ChangeSubType: ChangeSubType;
    private tempoInBpm;
    static getDefaultValueForInstTempo(instTempo: InstTempo): number;
    static isInputStringInstantaneousTempo(inputString: string): boolean;
    get Label(): string;
    set Label(value: string);
    get Placement(): PlacementEnum;
    set Placement(value: PlacementEnum);
    get StaffNumber(): number;
    set StaffNumber(value: number);
    get TempoInBpm(): number;
    set TempoInBpm(value: number);
    get ParentMultiTempoExpression(): MultiTempoExpression;
    getAbsoluteTimestamp(): Fraction;
    getAbsoluteFloatTimestamp(): number;
    private setTempoAndTempoType;
}
export declare enum TempoType {
    none = 0,
    metronomeMark = 1,
    inst = 2,
    change = 3,
    addon = 4
}
export declare enum InstTempo {
    larghissimo = 0,
    grave = 1,
    lento = 2,
    largo = 3,
    larghetto = 4,
    adagio = 5,
    adagietto = 6,
    andanteModerato = 7,
    andante = 8,
    andantino = 9,
    moderato = 10,
    allegretto = 11,
    allegroModerato = 12,
    allegro = 13,
    vivace = 14,
    vivacissimo = 15,
    allegrissimo = 16,
    presto = 17,
    prestissimo = 18,
    lastRealTempo = 19
}
export declare enum ChangeSubType {
    none = 0,
    atempo = 1,
    doppioMovimento = 2,
    tempoprimo = 3
}
