import Vex from "vexflow";
import VF = Vex.Flow;
import { ColoringOptions, GraphicalNote, VisibilityOptions } from "../GraphicalNote";
import { Note } from "../../VoiceData/Note";
import { ClefInstruction } from "../../VoiceData/Instructions/ClefInstruction";
import { Pitch } from "../../../Common/DataObjects/Pitch";
import { Fraction } from "../../../Common/DataObjects/Fraction";
import { OctaveEnum } from "../../VoiceData/Expressions/ContinuousExpressions/OctaveShift";
import { GraphicalVoiceEntry } from "../GraphicalVoiceEntry";
import { KeyInstruction } from "../../VoiceData/Instructions/KeyInstruction";
import { EngravingRules } from "../EngravingRules";
/**
 * The VexFlow version of a [[GraphicalNote]].
 */
export declare class VexFlowGraphicalNote extends GraphicalNote {
    constructor(note: Note, parent: GraphicalVoiceEntry, activeClef: ClefInstruction, octaveShift: OctaveEnum, rules: EngravingRules, graphicalNoteLength?: Fraction);
    octaveShift: OctaveEnum;
    vfpitch: [string, string, ClefInstruction];
    vfnote: [VF.StemmableNote, number];
    vfnoteIndex: number;
    private clef;
    /**
     * Update the pitch of this note. Necessary in order to display accidentals correctly.
     * This is called by VexFlowGraphicalSymbolFactory.addGraphicalAccidental.
     * @param pitch
     */
    setAccidental(pitch: Pitch): void;
    drawPitch(pitch: Pitch): Pitch;
    Transpose(keyInstruction: KeyInstruction, activeClef: ClefInstruction, halfTones: number, octaveEnum: OctaveEnum): Pitch;
    /**
     * Set the VexFlow StaveNote corresponding to this GraphicalNote, together with its index in the chord.
     * @param note
     * @param index
     */
    setIndex(note: VF.StemmableNote, index: number): void;
    notehead(vfNote?: VF.StemmableNote): {
        line: number;
    };
    /**
     * Gets the clef for this note
     */
    Clef(): ClefInstruction;
    /**
     * Gets the id of the SVGGElement containing this note, given the SVGRenderer is used.
     * This is for low-level rendering hacks and should be used with caution.
     */
    getSVGId(): string;
    /** Toggle visibility of the note, making it and its stem and beams invisible for `false`.
     * By default, this will also hide the note's slurs and ties (see visibilityOptions).
     * (This only works with the default SVG backend, not with the Canvas backend/renderer)
     * To get a GraphicalNote from a Note, use osmd.EngravingRules.GNote(note).
     */
    setVisible(visible: boolean, visibilityOptions?: VisibilityOptions): void;
    /**
     * Gets the SVGGElement containing this note, given the SVGRenderer is used.
     * This is for low-level rendering hacks and should be used with caution.
     */
    getSVGGElement(): SVGGElement;
    /** Gets the SVG path element of the note's stem. */
    getStemSVG(): HTMLElement;
    /** Gets the SVG path elements of the beams starting on this note. */
    getBeamSVGs(): HTMLElement[];
    /** Gets the SVG path elements of the note's ledger lines. */
    getLedgerLineSVGs(): HTMLElement[];
    /** Gets the SVG path elements of the note's tie curves. */
    getTieSVGs(): HTMLElement[];
    /** Gets the SVG path elements of the note's slur curve. */
    getSlurSVGs(): HTMLElement[];
    getNoteheadSVGs(): HTMLElement[];
    getFlagSVG(): HTMLElement;
    getVFNoteSVG(): HTMLElement;
    getModifierSVGs(): HTMLElement[];
    /** Change the color of a note (without re-rendering). See ColoringOptions for options like applyToBeams etc.
     * This requires the SVG backend (default, instead of canvas backend).
     */
    setColor(color: string, coloringOptions?: ColoringOptions): void;
}
