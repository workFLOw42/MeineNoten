import { Note } from "../VoiceData/Note";
import { Fraction } from "../../Common/DataObjects/Fraction";
import { KeyInstruction } from "../VoiceData/Instructions/KeyInstruction";
import { ClefInstruction } from "../VoiceData/Instructions/ClefInstruction";
import { OctaveEnum } from "../VoiceData/Expressions/ContinuousExpressions/OctaveShift";
import { AccidentalEnum, Pitch } from "../../Common/DataObjects/Pitch";
import { GraphicalObject } from "./GraphicalObject";
import { GraphicalVoiceEntry } from "./GraphicalVoiceEntry";
import { GraphicalMusicPage } from "./GraphicalMusicPage";
import { EngravingRules } from "./EngravingRules";
/**
 * The graphical counterpart of a [[Note]]
 */
export declare class GraphicalNote extends GraphicalObject {
    constructor(note: Note, parent: GraphicalVoiceEntry, rules: EngravingRules, graphicalNoteLength?: Fraction);
    sourceNote: Note;
    DrawnAccidental: AccidentalEnum;
    graphicalNoteLength: Fraction;
    parentVoiceEntry: GraphicalVoiceEntry;
    numberOfDots: number;
    rules: EngravingRules;
    staffLine: number;
    baseFingeringXOffset: number;
    baseStringNumberXOffset: number;
    lineShift: number;
    Transpose(keyInstruction: KeyInstruction, activeClef: ClefInstruction, halfTones: number, octaveEnum: OctaveEnum): Pitch;
    /**
     * Return the number of dots needed to represent the given fraction.
     * @param fraction
     * @returns {number}
     */
    private calculateNumberOfNeededDots;
    get ParentMusicPage(): GraphicalMusicPage;
    /** Get a GraphicalNote from a Note. Use osmd.rules as the second parameter (instance reference).
     *  Also more easily available via osmd.rules.GNote(note). */
    static FromNote(note: Note, rules: EngravingRules): GraphicalNote;
    ToStringShort(octaveOffset?: number): string;
    get ToStringShortGet(): string;
    getLyricsSVGs(): HTMLElement[];
    /** Change the color of a note (without re-rendering). See ColoringOptions for options like applyToBeams etc.
     * This requires the SVG backend (default, instead of canvas backend).
     */
    setColor(color: string, coloringOptions: ColoringOptions): void;
    /** Toggle visibility of the note, making it and its stem and beams invisible for `false`.
     * By default, this will also hide the note's slurs and ties (see visibilityOptions).
     * (This only works with the default SVG backend, not with the Canvas backend/renderer)
     * To get a GraphicalNote from a Note, use osmd.EngravingRules.GNote(note).
     */
    setVisible(visible: boolean, visibilityOptions?: VisibilityOptions): void;
}
/** Coloring options for VexFlowGraphicalNote.setColor(). */
export interface ColoringOptions {
    applyToBeams?: boolean;
    applyToFlag?: boolean;
    applyToLedgerLines?: boolean;
    applyToLyrics?: boolean;
    applyToModifiers?: boolean;
    applyToMultiRestMeasure?: boolean;
    /** Whether to apply the color to the number indicating how many measures the rest lasts (not the measure number). */
    applyToMultiRestMeasureNumber?: boolean;
    /** Whether to apply the color to the wide bar within the stafflines (looks about like `|----|`). */
    applyToMultiRestMeasureRestBar?: boolean;
    applyToNoteheads?: boolean;
    applyToSlurs?: boolean;
    applyToStem?: boolean;
    applyToTies?: boolean;
}
/** Visibility options for VexFlowGraphicalNote.setVisible().
 * E.g. if setVisible(false, {applyToTies: false}), everything about a note will be invisible except its ties.
 * */
export interface VisibilityOptions {
    applyToBeams?: boolean;
    applyToLedgerLines?: boolean;
    applyToNotehead?: boolean;
    applyToSlurs?: boolean;
    applyToStem?: boolean;
    applyToTies?: boolean;
}
