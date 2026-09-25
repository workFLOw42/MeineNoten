import { IXmlElement } from "../../../Common/FileIO/Xml";
import { MusicSheet } from "../../MusicSheet";
import { ChordSymbolContainer } from "../../VoiceData/ChordSymbolContainer";
import { KeyInstruction } from "../../VoiceData/Instructions/KeyInstruction";
import { PlacementEnum } from "../../VoiceData/Expressions/AbstractExpression";
export declare class ChordSymbolReader {
    static readChordSymbol(xmlNode: IXmlElement, musicSheet: MusicSheet, activeKey: KeyInstruction): ChordSymbolContainer;
    static readPlacement(node: IXmlElement): PlacementEnum;
}
