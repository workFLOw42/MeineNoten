import { IXmlElement } from "./Xml";
import JSZip from "jszip";
export declare class MXLFile {
    private blob;
    zipFile: JSZip;
    xmlText: string;
    /** Set after tryUnzip(). True if it could be unzipped successfully. */
    unzipSuccessful: boolean;
    constructor(blob: Blob);
    /** Try unzipping to see if this is a zip file.
     * This is a separate method so that we don't need to unzip twice to check whether it's a zip file.
     */
    tryUnzip(): Promise<boolean>;
    getXmlString(): Promise<string>;
}
/**
 * Some helper methods to handle MXL files.
 */
export declare class MXLHelper {
    /** Returns the documentElement of MXL data. */
    static MXLtoIXmlElement(data: string): Promise<IXmlElement>;
    static jszipToXMLstring(zip: JSZip): Promise<string>;
    static MXLtoXMLstring(data: string | Blob): Promise<string>;
}
